//! Downloads that reach the game folder only when their checksum matches the source's.
use super::{FileRef, safe_name};
use sha1::Sha1;
use sha2::{Digest, Sha256, Sha512};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

/// Hosts a file may come from: Modrinth's and CurseForge's file servers, and the release assets
/// of Horizon's registry (and, see [`github_release_asset`], of the repository a registry entry
/// names).
const HOSTS: [&str; 5] = [
    "https://cdn.modrinth.com/",
    "https://edge.forgecdn.net/",
    "https://mediafilez.forgecdn.net/",
    horizon_mods::index::FILE_HOST,
    // The registry's files at their address before the move to horizon-rust (GitHub redirects it).
    "https://github.com/ludovic111/horizon-mods/releases/download/",
];
/// `https://github.com/<owner>/<repo>/releases/download/<tag>/<name>`: a release asset of a mod's
/// own repository, which the registry's index lists once its release passed the registry's checks
/// (docs/api/mod-index.md). Only the address is vouched for here; the index's SHA-256 is what
/// pins the bytes, and [`save`] refuses any other.
pub fn github_release_asset(url: &str) -> bool {
    let Some(rest) = url.strip_prefix("https://github.com/") else {
        return false;
    };
    let parts: Vec<&str> = rest.split('/').collect();
    let plain = |p: &str| {
        !p.is_empty()
            && p != "."
            && p != ".."
            && p.bytes()
                .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'+' | b'%'))
    };
    matches!(parts[..], [owner, repo, "releases", "download", tag, name]
        if [owner, repo, tag, name].iter().all(|p| plain(p)))
}

/// Bigger than any pack these sources serve (Modrinth caps uploads at 500 MiB).
const MAX: u64 = 1 << 30;

/// The HTTP agent the sources share, reaching `url` through the player's proxy when one is set
/// (`proxy`). Modrinth asks for a user agent that names the project.
///
/// It is kept between downloads: a fresh agent starts a new connection pool, so one per file would
/// be a handshake per file, and a modpack brings hundreds.
pub(super) fn agent(url: &str) -> ureq::Agent {
    let spec = crate::proxy::spec_for(url);
    horizon_port_pack::net::keep("browser", spec.as_deref(), |proxy| {
        let builder = ureq::AgentBuilder::new()
            .user_agent(&format!(
                "horizon-rust/horizon horizon-launcher/{} (github.com/horizon-rust/horizon)",
                crate::backend::VERSION
            ))
            .timeout_connect(std::time::Duration::from_secs(15))
            .timeout_read(std::time::Duration::from_secs(60));
        match proxy {
            Some(proxy) => builder.proxy(proxy).build(),
            None => builder.build(),
        }
    })
}

/// Why a file can't be downloaded at all, before any byte is read.
pub fn refuse(file: &FileRef) -> Option<String> {
    if !HOSTS.iter().any(|h| file.url.starts_with(h)) && !github_release_asset(&file.url) {
        return Some(format!(
            "{} isn't on Modrinth's, CurseForge's or Horizon's file servers, or a GitHub release.",
            file.url
        ));
    }
    refuse_file(file)
}

/// [`refuse`] without the host: the name, the checksum and the size.
fn refuse_file(file: &FileRef) -> Option<String> {
    if safe_name(&file.name).is_none() {
        return Some(format!("{:?} isn't a plain file name.", file.name));
    }
    if file.checksum().is_none() {
        return Some("Its source gives no checksum, so it can't be checked.".into());
    }
    if file.size > MAX {
        return Some("It is larger than any pack should be.".into());
    }
    None
}

/// Hashes as they are written: the strongest one the file lists.
enum Hasher {
    Sha512(Sha512),
    Sha256(Sha256),
    Sha1(Sha1),
}

impl Hasher {
    fn for_file(file: &FileRef) -> Self {
        match file.checksum() {
            Some(("SHA-512", _)) => Hasher::Sha512(Sha512::new()),
            Some(("SHA-256", _)) => Hasher::Sha256(Sha256::new()),
            _ => Hasher::Sha1(Sha1::new()),
        }
    }
    fn update(&mut self, bytes: &[u8]) {
        match self {
            Hasher::Sha512(h) => h.update(bytes),
            Hasher::Sha256(h) => h.update(bytes),
            Hasher::Sha1(h) => h.update(bytes),
        }
    }
    fn hex(self) -> String {
        let bytes: Vec<u8> = match self {
            Hasher::Sha512(h) => h.finalize().to_vec(),
            Hasher::Sha256(h) => h.finalize().to_vec(),
            Hasher::Sha1(h) => h.finalize().to_vec(),
        };
        bytes.iter().map(|b| format!("{b:02x}")).collect()
    }
}

/// Copies `reader` into `<folder>/<file.name>`, hashing on the way, and keeps it only when the
/// size and checksum match: until then it is `.<name>.part`, removed on any failure.
/// `progress` gets the fraction done.
pub fn save(
    file: &FileRef,
    reader: impl Read,
    folder: &Path,
    progress: &dyn Fn(f32),
) -> Result<PathBuf, String> {
    if let Some(why) = refuse(file) {
        return Err(why);
    }
    save_from_anywhere(file, reader, folder, progress)
}

/// [`save`] for a file that comes from no host: a local test registry's.
pub(super) fn save_from_anywhere(
    file: &FileRef,
    mut reader: impl Read,
    folder: &Path,
    progress: &dyn Fn(f32),
) -> Result<PathBuf, String> {
    if let Some(why) = refuse_file(file) {
        return Err(why);
    }
    let (algo, want) = file.checksum().expect("refuse checks for one");
    fs::create_dir_all(folder).map_err(|e| format!("{}: {e}", folder.display()))?;
    let target = folder.join(&file.name);
    let part = folder.join(format!(".{}.part", file.name));
    let result = (|| {
        let mut out = fs::File::create(&part).map_err(|e| format!("{}: {e}", part.display()))?;
        let mut hasher = Hasher::for_file(file);
        let (mut done, mut buf) = (0u64, vec![0u8; 1 << 16]);
        loop {
            let n = reader
                .read(&mut buf)
                .map_err(|e| format!("Download interrupted: {e}"))?;
            if n == 0 {
                break;
            }
            done += n as u64;
            if done > MAX || (file.size > 0 && done > file.size) {
                return Err("The download is larger than its source says.".to_string());
            }
            hasher.update(&buf[..n]);
            out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
            progress(done as f32 / file.size.max(1) as f32);
        }
        if file.size > 0 && done != file.size {
            return Err(format!("Got {done} bytes of {}.", file.size));
        }
        let got = hasher.hex();
        if !got.eq_ignore_ascii_case(want) {
            return Err(format!(
                "The {algo} checksum doesn't match: the file isn't the one its source lists. \
                 Nothing was installed."
            ));
        }
        out.sync_all().map_err(|e| e.to_string())?;
        drop(out);
        fs::rename(&part, &target).map_err(|e| format!("{}: {e}", target.display()))
    })();
    if result.is_err() {
        let _ = fs::remove_file(&part);
    }
    result.map(|()| target)
}

/// Downloads `file` into `folder`, utilizing multithreaded chunk requests when supported.
pub fn fetch(file: &FileRef, folder: &Path, progress: &dyn Fn(f32)) -> Result<PathBuf, String> {
    if let Some(why) = refuse(file) {
        return Err(why);
    }
    // Attempt multithreaded chunk download for files >= 1 MiB
    if file.size >= 1024 * 1024 {
        if let Ok(path) = fetch_multithreaded(file, folder, progress) {
            return Ok(path);
        }
    }
    fetch_single(file, folder, progress)
}

/// Single-stream download fallback.
pub fn fetch_single(file: &FileRef, folder: &Path, progress: &dyn Fn(f32)) -> Result<PathBuf, String> {
    if let Some(why) = refuse(file) {
        return Err(why);
    }
    let response = agent(&file.url)
        .get(&file.url)
        .call()
        .map_err(|e| match e {
            ureq::Error::Status(code, _) => format!("{} answered {code}.", file.url),
            ureq::Error::Transport(t) => format!("No connection: {t}"),
        })?;
    save(file, response.into_reader(), folder, progress)
}

/// Parallel chunk downloader using HTTP Range requests.
pub fn fetch_multithreaded(
    file: &FileRef,
    folder: &Path,
    progress: &dyn Fn(f32),
) -> Result<PathBuf, String> {
    if let Some(why) = refuse(file) {
        return Err(why);
    }
    let (algo, want) = file.checksum().expect("refuse checks for one");
    fs::create_dir_all(folder).map_err(|e| format!("{}: {e}", folder.display()))?;
    let target = folder.join(&file.name);
    let part = folder.join(format!(".{}.part", file.name));

    let total_size = file.size;
    let num_threads = 4.min((total_size / (256 * 1024)).max(1) as usize);
    if num_threads <= 1 {
        return fetch_single(file, folder, progress);
    }

    let chunk_size = (total_size + num_threads as u64 - 1) / num_threads as u64;
    let done_bytes = std::sync::Arc::new(std::sync::atomic::AtomicU64::new(0));

    let mut handles = Vec::with_capacity(num_threads);
    for i in 0..num_threads {
        let start = i as u64 * chunk_size;
        let end = ((i as u64 + 1) * chunk_size - 1).min(total_size - 1);
        if start > end {
            break;
        }
        let url = file.url.clone();
        let chunk_path = folder.join(format!(".{}.chunk.{}", file.name, i));
        let done_clone = done_bytes.clone();

        handles.push(std::thread::spawn(move || -> Result<PathBuf, String> {
            let resp = agent(&url)
                .get(&url)
                .set("Range", &format!("bytes={start}-{end}"))
                .call()
                .map_err(|e| format!("Range request failed: {e}"))?;

            if resp.status() != 206 {
                return Err(format!("Server does not support range requests (status {})", resp.status()));
            }

            let mut reader = resp.into_reader();
            let mut chunk_file = fs::File::create(&chunk_path)
                .map_err(|e| format!("Failed to create chunk file: {e}"))?;
            let mut buf = vec![0u8; 1 << 16];
            loop {
                let n = reader.read(&mut buf).map_err(|e| e.to_string())?;
                if n == 0 {
                    break;
                }
                chunk_file.write_all(&buf[..n]).map_err(|e| e.to_string())?;
                done_clone.fetch_add(n as u64, std::sync::atomic::Ordering::Relaxed);
            }
            chunk_file.sync_all().map_err(|e| e.to_string())?;
            Ok(chunk_path)
        }));
    }

    let mut chunk_paths = Vec::new();
    for handle in handles {
        let chunk_path = match handle.join() {
            Ok(Ok(p)) => p,
            _ => {
                for cp in &chunk_paths {
                    let _ = fs::remove_file(cp);
                }
                return Err("Chunk download worker failed".to_string());
            }
        };
        chunk_paths.push(chunk_path);
        progress(done_bytes.load(std::sync::atomic::Ordering::Relaxed) as f32 / total_size.max(1) as f32);
    }

    let stitch_result = (|| -> Result<(), String> {
        let mut out = fs::File::create(&part).map_err(|e| format!("{}: {e}", part.display()))?;
        let mut hasher = Hasher::for_file(file);
        let mut buf = vec![0u8; 1 << 16];

        for chunk_path in &chunk_paths {
            let mut chunk_file = fs::File::open(chunk_path).map_err(|e| e.to_string())?;
            loop {
                let n = chunk_file.read(&mut buf).map_err(|e| e.to_string())?;
                if n == 0 {
                    break;
                }
                hasher.update(&buf[..n]);
                out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
            }
        }

        let got = hasher.hex();
        if !got.eq_ignore_ascii_case(want) {
            return Err(format!(
                "The {algo} checksum doesn't match: the file isn't the one its source lists."
            ));
        }

        out.sync_all().map_err(|e| e.to_string())?;
        drop(out);
        fs::rename(&part, &target).map_err(|e| format!("{}: {e}", target.display()))
    })();

    for chunk_path in &chunk_paths {
        let _ = fs::remove_file(chunk_path);
    }

    if stitch_result.is_err() {
        let _ = fs::remove_file(&part);
        stitch_result?;
    }

    progress(1.0);
    Ok(target)
}
