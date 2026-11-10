package qa.sijill.sijill.workspace.google;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

import qa.sijill.sijill.workspace.DriveType;

/**
 * Settings of the Google Drive connector ({@code sijill.google.drive.*}).
 *
 * @param enabled        switches the connector (and the Drive-reading classifier) on
 * @param accounts       the Google accounts whose Drives are read; each must sign in to Sijill once so its
 *                       token is stored, and a check fails while one of them has not
 * @param rootFolderId   the folder that is scanned, with everything under it, or {@code root} for the
 *                       account's whole My Drive. Required
 * @param internalDomains e-mail domains treated as "inside the network" (everyone else is outside)
 * @param internalEmails  individual addresses treated as inside the network; the accounts read always are
 * @param driveType      what the scanned folder counts as for rule R1: SHARED stands in for a school
 *                       shared drive (default), PERSONAL makes every file break R1
 * @param maxFiles       safety limit on files per run; a Drive with more makes the check fail
 * @param maxBytes       files larger than this are not read (they go to "Needs review")
 * @param apiBase        the Drive API address; only changed for tests against a stand-in server
 */
@ConfigurationProperties("sijill.google.drive")
public record GoogleDriveProperties(boolean enabled, List<String> accounts, String rootFolderId, List<String> internalDomains,
                                    List<String> internalEmails, DriveType driveType, Integer maxFiles, Long maxBytes,
                                    String apiBase) {

    public GoogleDriveProperties {
        accounts = accounts == null ? List.of() : accounts;
        internalDomains = internalDomains == null ? List.of() : internalDomains;
        internalEmails = internalEmails == null ? List.of() : internalEmails;
        driveType = driveType == null ? DriveType.SHARED : driveType;
        maxFiles = maxFiles == null ? 500 : maxFiles;
        maxBytes = maxBytes == null ? 15L * 1024 * 1024 : maxBytes;
        apiBase = apiBase == null || apiBase.isBlank() ? "https://www.googleapis.com/drive/v3" : apiBase;
    }

    /** Is this address inside the network? The accounts being read always are. */
    public boolean isInternal(String email) {
        if (email == null) {
            return false;
        }
        String e = email.toLowerCase();
        String domain = e.substring(e.indexOf('@') + 1);
        return accounts.stream().anyMatch(x -> x.equalsIgnoreCase(e))
                || internalEmails.stream().anyMatch(x -> x.equalsIgnoreCase(e))
                || internalDomains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }
}
