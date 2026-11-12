package qa.hafez.hafez.workspace.google;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.ShareTargetType;
import qa.hafez.hafez.workspace.WorkspaceConnector;
import qa.hafez.hafez.workspace.WorkspaceContent;
import qa.hafez.hafez.workspace.WorkspaceFile;
import qa.hafez.hafez.workspace.WorkspacePermission;
import qa.hafez.hafez.workspace.WorkspacePerson;

/**
 * Reads the Google Drives of the configured accounts, each as that account after it has signed in to Hafez.
 * The folder is {@code hafez.google.drive.root-folder-id}; the value {@code root} means each
 * account's whole My Drive (files only "shared with me" are not part of it).
 *
 * <p><b>Read-only:</b> it only calls {@code GET} on the Drive API and the token carries the
 * {@code drive.readonly} scope, so Google itself refuses any change.
 *
 * <p>This is the personal-account test connector. A school deployment would use a service account with
 * domain-wide delegation behind the same {@link WorkspaceConnector} interface.
 */
@Component
@ConditionalOnProperty(name = "hafez.google.drive.enabled", havingValue = "true")
public class GoogleDriveConnector implements WorkspaceConnector {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveConnector.class);
    private static final String FOLDER = "application/vnd.google-apps.folder";
    private static final String MY_DRIVE = "root";
    private static final String SHORTCUT = "application/vnd.google-apps.shortcut";
    private static final String FILE_FIELDS = "id,name,mimeType,parents,md5Checksum,modifiedTime,trashedTime,size,"
            + "owners(emailAddress),permissions(emailAddress,type,role,domain)";

    // ---- Drive API response shapes (only the fields requested above) ----
    record DriveList(List<DriveFile> files, String nextPageToken) {}
    record DriveFile(String id, String name, String mimeType, List<String> parents, String md5Checksum,
                     String modifiedTime, String trashedTime, String size, List<Owner> owners, List<Perm> permissions) {}
    record Owner(String emailAddress) {}
    record Perm(String emailAddress, String type, String role, String domain) {}
    private record Folder(String id, String path) {}
    private record FolderInfo(String name, String parent) {}

    private final GoogleDriveProperties props;
    private final GoogleTokenProvider tokens;
    private final RestClient http;
    private final Map<String, FolderInfo> folderCache = new ConcurrentHashMap<>();
    private final Map<String, String> myDriveIds = new ConcurrentHashMap<>();

    GoogleDriveConnector(GoogleDriveProperties props, GoogleTokenProvider tokens) {
        this.props = props;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(props.apiBase()).build();
    }

    // ================================================================== WorkspaceConnector

    @Override
    public List<WorkspaceFile> listAllFiles(String domain) {
        return readAll();
    }

    /**
     * A sharing change does not change a file's modified time, so the "changes since" view re-reads the
     * Drives in full. That is cheap for test accounts; a school deployment would use the Changes API.
     */
    @Override
    public List<WorkspaceFile> listChanges(String domain, Instant since) {
        return readAll();
    }

    /** Asks each account in turn; Google answers "not found" for a file an account cannot open. */
    @Override
    public Optional<WorkspaceFile> getFile(String domain, String workspaceFileId) {
        for (String account : accounts()) {
            DriveFile d;
            try {
                d = http.get().uri(u -> u.path("/files/{id}").queryParam("fields", "{f}").queryParam("supportsAllDrives", "true")
                                .build(Map.of("id", workspaceFileId, "f", FILE_FIELDS)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(DriveFile.class);
            } catch (HttpClientErrorException.NotFound e) {
                continue;
            }
            String path = d == null ? null : pathOf(d, account);
            if (path != null) {
                return Optional.of(toWorkspace(d, path, account));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<WorkspacePermission> getPermissions(String domain, String workspaceFileId) {
        return getFile(domain, workspaceFileId).map(WorkspaceFile::permissions).orElse(List.of());
    }

    /** A personal account has no school directory; an empty list changes nobody's role. */
    @Override
    public List<WorkspacePerson> getPeople(String domain) {
        return List.of();
    }

    @Override
    public Optional<WorkspaceContent> readContent(String domain, String workspaceFileId) {
        for (String account : accounts()) {
            try {
                return readContentAs(account, workspaceFileId);
            } catch (HttpClientErrorException.NotFound e) {
                // this account cannot open the file: try the next one
            }
        }
        return Optional.empty();
    }

    private Optional<WorkspaceContent> readContentAs(String account, String workspaceFileId) {
        DriveFile d = http.get().uri(u -> u.path("/files/{id}").queryParam("fields", "id,name,mimeType,size")
                        .queryParam("supportsAllDrives", "true").build(Map.of("id", workspaceFileId)))
                .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(DriveFile.class);
        if (d == null) {
            return Optional.empty();
        }
        String mime = d.mimeType() == null ? "" : d.mimeType();
        byte[] data;
        String outType = mime;
        if (mime.startsWith("application/vnd.google-apps.")) {
            outType = switch (mime) {
                case "application/vnd.google-apps.document", "application/vnd.google-apps.presentation" -> "text/plain";
                case "application/vnd.google-apps.spreadsheet" -> "text/csv";
                default -> null;
            };
            if (outType == null) {
                return Optional.empty();
            }
            String exportType = outType;
            data = http.get().uri(u -> u.path("/files/{id}/export").queryParam("mimeType", "{m}")
                            .build(Map.of("id", workspaceFileId, "m", exportType)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(byte[].class);
        } else {
            long size = d.size() == null ? 0 : Long.parseLong(d.size());
            if (size > props.maxBytes()) {
                log.info("File {} is {} bytes, over the {} byte limit: not read", workspaceFileId, size, props.maxBytes());
                return Optional.empty();
            }
            data = http.get().uri(u -> u.path("/files/{id}").queryParam("alt", "media").queryParam("supportsAllDrives", "true")
                            .build(Map.of("id", workspaceFileId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(byte[].class);
        }
        return data == null ? Optional.empty() : Optional.of(new WorkspaceContent(d.name(), outType, data));
    }

    // ================================================================== reading the Drives

    /**
     * Reads every configured Drive, or the one configured folder through the first account that can open
     * it. A file two accounts can both see is returned once. Any failure stops the whole read: a partial
     * list would make the missing files look deleted.
     */
    private List<WorkspaceFile> readAll() {
        Map<String, WorkspaceFile> out = new LinkedHashMap<>();
        if (scansMyDrive()) {
            for (String account : accounts()) {
                walk(account, out);
            }
        } else {
            boolean read = false;
            for (String account : accounts()) {
                try {
                    walk(account, out);
                    read = true;
                    break;
                } catch (HttpClientErrorException.NotFound e) {
                    // this account cannot open the folder: try the next one
                }
            }
            if (!read) {
                throw new IllegalStateException("None of the configured accounts can open the Drive folder.");
            }
        }
        log.info("Read {} files from {} Drive account(s)", out.size(), accounts().size());
        return new ArrayList<>(out.values());
    }

    /** Walks one account's scanned folder breadth-first. Each file's folder path is relative to that folder. */
    private void walk(String account, Map<String, WorkspaceFile> out) {
        Deque<Folder> queue = new ArrayDeque<>();
        queue.add(new Folder(rootFor(account), ""));
        while (!queue.isEmpty()) {
            Folder folder = queue.poll();
            String page = null;
            do {
                DriveList list = list(account, "'" + folder.id() + "' in parents and trashed = false", page);
                for (DriveFile d : list.files() == null ? List.<DriveFile>of() : list.files()) {
                    if (FOLDER.equals(d.mimeType())) {
                        String path = folder.path().isEmpty() ? d.name() : folder.path() + "/" + d.name();
                        folderCache.put(d.id(), new FolderInfo(d.name(), folder.id()));
                        queue.add(new Folder(d.id(), path));
                    } else if (!SHORTCUT.equals(d.mimeType())) {
                        out.putIfAbsent(d.id(), toWorkspace(d, folder.path(), account));
                        if (out.size() > props.maxFiles()) {
                            // never return a partial list: files left out would be recorded as deleted
                            throw new IllegalStateException("The Drives have more than " + props.maxFiles()
                                    + " files. Raise hafez.google.drive.max-files.");
                        }
                    }
                }
                page = list.nextPageToken();
            } while (page != null);
        }
    }

    private DriveList list(String account, String query, String pageToken) {
        Map<String, String> vars = new HashMap<>();
        vars.put("q", query);
        vars.put("f", "nextPageToken,files(" + FILE_FIELDS + ")");
        vars.put("t", pageToken == null ? "" : pageToken);
        return http.get().uri(u -> {
                    var b = u.path("/files").queryParam("q", "{q}").queryParam("fields", "{f}").queryParam("pageSize", "200")
                            .queryParam("supportsAllDrives", "true").queryParam("includeItemsFromAllDrives", "true");
                    if (pageToken != null) {
                        b.queryParam("pageToken", "{t}");
                    }
                    return b.build(vars);
                })
                .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(DriveList.class);
    }

    /** The folder path of a file relative to the scanned folder, or null if the file is outside it. */
    private String pathOf(DriveFile file, String account) {
        String root = rootFor(account);
        List<String> names = new ArrayList<>();
        String parent = file.parents() == null || file.parents().isEmpty() ? null : file.parents().get(0);
        for (int depth = 0; parent != null && depth < 25; depth++) {
            if (parent.equals(root)) {
                java.util.Collections.reverse(names);
                return String.join("/", names);
            }
            FolderInfo info = folderCache.get(parent);
            if (info == null) {
                info = fetchFolder(parent, account);
                if (info == null) {
                    return null;
                }
                folderCache.put(parent, info);
            }
            names.add(info.name());
            parent = info.parent();
        }
        return null;
    }

    private FolderInfo fetchFolder(String id, String account) {
        try {
            DriveFile d = http.get().uri(u -> u.path("/files/{id}").queryParam("fields", "id,name,parents")
                            .queryParam("supportsAllDrives", "true").build(Map.of("id", id)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(DriveFile.class);
            return d == null ? null : new FolderInfo(d.name(), d.parents() == null || d.parents().isEmpty() ? null : d.parents().get(0));
        } catch (HttpClientErrorException e) {
            return null;
        }
    }

    // ================================================================== mapping

    private WorkspaceFile toWorkspace(DriveFile d, String folderPath, String account) {
        String owner = d.owners() == null || d.owners().isEmpty() ? account : d.owners().get(0).emailAddress();
        List<WorkspacePermission> permissions = new ArrayList<>();
        for (Perm p : d.permissions() == null ? List.<Perm>of() : d.permissions()) {
            permissions.add(toPermission(p));
        }
        return new WorkspaceFile(d.id(), d.name(), owner, d.mimeType(), props.driveType(), folderPath, d.md5Checksum(),
                parse(d.modifiedTime()), parse(d.trashedTime()), permissions);
    }

    private WorkspacePermission toPermission(Perm p) {
        AccessLevel level = switch (p.role() == null ? "" : p.role()) {
            case "owner", "organizer" -> AccessLevel.OWNER;
            case "writer", "fileOrganizer" -> AccessLevel.EDITOR;
            case "commenter" -> AccessLevel.COMMENTER;
            default -> AccessLevel.VIEWER;
        };
        return switch (p.type() == null ? "" : p.type()) {
            case "anyone" -> new WorkspacePermission(null, ShareTargetType.ANYONE_WITH_LINK, level);
            case "domain" -> props.internalDomains().stream().anyMatch(x -> x.equalsIgnoreCase(p.domain()))
                    ? new WorkspacePermission(p.domain(), ShareTargetType.ALL_STAFF, level)
                    : new WorkspacePermission(p.domain(), ShareTargetType.EXTERNAL_USER, level);
            case "group" -> new WorkspacePermission(p.emailAddress(),
                    internal(p.emailAddress()) ? ShareTargetType.INTERNAL_GROUP : ShareTargetType.EXTERNAL_USER, level);
            default -> new WorkspacePermission(p.emailAddress(),
                    internal(p.emailAddress()) ? ShareTargetType.INTERNAL_USER : ShareTargetType.EXTERNAL_USER, level);
        };
    }

    private static Instant parse(String time) {
        return time == null || time.isBlank() ? null : Instant.parse(time);
    }

    private volatile java.util.Set<String> signedInCache = java.util.Set.of();
    private volatile long signedInCachedAt;

    /** Accounts that have signed in to Hafez, looked up at most every 20 seconds (a check asks for it per permission). */
    private java.util.Set<String> signedIn() {
        long now = System.currentTimeMillis();
        if (now - signedInCachedAt > 20_000) {
            List<String> found = tokens.signedInAccounts();
            java.util.Set<String> next = new java.util.LinkedHashSet<>();
            if (found != null) {
                found.forEach(a -> next.add(a.toLowerCase()));
            }
            signedInCache = next;
            signedInCachedAt = now;
        }
        return signedInCache;
    }

    /** The configured accounts plus everybody with a Hafez account who has signed in (their sign-in is the consent). */
    private List<String> accounts() {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        props.accounts().forEach(a -> all.add(a.toLowerCase()));
        all.addAll(signedIn());
        if (all.isEmpty()) {
            throw new IllegalStateException("No Google account has signed in yet. Sign in to Hafez with a school account, "
                    + "or list the accounts in hafez.google.drive.accounts.");
        }
        return new ArrayList<>(all);
    }

    /** Inside the network: the configured rules, or an account whose Drive is read. */
    private boolean internal(String email) {
        return props.isInternal(email) || (email != null && signedIn().contains(email.toLowerCase()));
    }

    private boolean scansMyDrive() {
        return MY_DRIVE.equalsIgnoreCase(configuredRoot());
    }

    private String configuredRoot() {
        if (props.rootFolderId() == null || props.rootFolderId().isBlank()) {
            throw new IllegalStateException("Set hafez.google.drive.root-folder-id: a folder ID, or root for each account's whole My Drive.");
        }
        return props.rootFolderId().trim();
    }

    /** The real ID of the scanned folder: files report that as their parent, never the alias "root". */
    private String rootFor(String account) {
        if (!scansMyDrive()) {
            return configuredRoot();
        }
        String id = myDriveIds.get(account);
        if (id == null) {
            DriveFile d = http.get().uri(u -> u.path("/files/root").queryParam("fields", "id").build())
                    .header(HttpHeaders.AUTHORIZATION, bearer(account)).retrieve().body(DriveFile.class);
            if (d == null || d.id() == null) {
                throw new IllegalStateException("Google did not report the My Drive of " + account + ".");
            }
            id = d.id();
            myDriveIds.put(account, id);
        }
        return id;
    }

    private String bearer(String account) {
        return "Bearer " + tokens.accessToken(account);
    }
}
