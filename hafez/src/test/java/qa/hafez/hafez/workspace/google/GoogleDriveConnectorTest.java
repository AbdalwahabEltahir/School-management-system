package qa.hafez.hafez.workspace.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import qa.hafez.hafez.workspace.WorkspaceFile;

/** Runs the connector against a small local stand-in for the Drive API. */
class GoogleDriveConnectorTest {

    private static final String FOLDER = "\"mimeType\":\"application/vnd.google-apps.folder\"";
    private static final String DOC = "\"mimeType\":\"text/plain\"";

    private final Map<String, String> childrenOf = new HashMap<>();
    private final Map<String, String> byId = new HashMap<>();
    private final Map<String, String> childrenOfB = new HashMap<>();
    private final Map<String, String> byIdB = new HashMap<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = URLDecoder.decode(String.valueOf(exchange.getRequestURI().getRawQuery()), StandardCharsets.UTF_8);
            boolean second = "Bearer token-b".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = "{}";
            int status = 200;
            if (path.equals("/files")) {
                String parent = query.substring(query.indexOf('\'') + 1, query.indexOf("' in parents"));
                body = "{\"files\":[" + (second ? childrenOfB : childrenOf).getOrDefault(parent, "") + "]}";
            } else if (path.startsWith("/files/")) {
                body = (second ? byIdB : byId).get(path.substring("/files/".length()));
                status = body == null ? 404 : 200;
                body = body == null ? "{}" : body;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        byId.put("root", "{\"id\":\"REAL_ROOT\"}");
        childrenOf.put("REAL_ROOT", "{\"id\":\"F1\",\"name\":\"Student Records\"," + FOLDER + ",\"parents\":[\"REAL_ROOT\"]},"
                + "{\"id\":\"A\",\"name\":\"top.txt\"," + DOC + ",\"parents\":[\"REAL_ROOT\"]}");
        childrenOf.put("F1", "{\"id\":\"B\",\"name\":\"marks.txt\"," + DOC + ",\"parents\":[\"F1\"]}");
        byId.put("F1", "{\"id\":\"F1\",\"name\":\"Student Records\",\"parents\":[\"REAL_ROOT\"]}");
        byId.put("B", "{\"id\":\"B\",\"name\":\"marks.txt\"," + DOC + ",\"parents\":[\"F1\"]}");
        byIdB.put("root", "{\"id\":\"ROOT_B\"}");
        childrenOfB.put("ROOT_B", "{\"id\":\"C\",\"name\":\"second.txt\"," + DOC + ",\"parents\":[\"ROOT_B\"]},"
                + "{\"id\":\"A\",\"name\":\"top.txt\"," + DOC + ",\"parents\":[\"ROOT_B\"]}");
        byIdB.put("C", "{\"id\":\"C\",\"name\":\"second.txt\"," + DOC + ",\"parents\":[\"ROOT_B\"]}");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GoogleDriveConnector connector(String root, int maxFiles) {
        GoogleTokenProvider tokens = mock(GoogleTokenProvider.class);
        when(tokens.accessToken("teacher@gmail.com")).thenReturn("token");
        return new GoogleDriveConnector(new GoogleDriveProperties(true, List.of("teacher@gmail.com"), root, null, null, null, maxFiles, null,
                "http://127.0.0.1:" + server.getAddress().getPort()), tokens);
    }

    private GoogleDriveConnector twoAccounts(GoogleTokenProvider tokens) {
        when(tokens.accessToken("a@gmail.com")).thenReturn("token-a");
        return new GoogleDriveConnector(new GoogleDriveProperties(true, List.of("a@gmail.com", "b@gmail.com"), "root", null, null, null,
                10, null, "http://127.0.0.1:" + server.getAddress().getPort()), tokens);
    }

    @Test
    void anAccountThatSignedInIsReadEvenWhenItIsNotInTheConfiguredList() {
        GoogleTokenProvider tokens = mock(GoogleTokenProvider.class);
        when(tokens.signedInAccounts()).thenReturn(List.of("teacher@gmail.com"));
        when(tokens.accessToken("teacher@gmail.com")).thenReturn("token");
        GoogleDriveConnector connector = new GoogleDriveConnector(new GoogleDriveProperties(true, List.of(), "root", null, null, null, 10, null,
                "http://127.0.0.1:" + server.getAddress().getPort()), tokens);
        assertThat(connector.listAllFiles("gmail.com")).extracting(WorkspaceFile::name).contains("top.txt", "marks.txt");
    }

    @Test
    void everyAccountIsReadAndASharedFileAppearsOnce() {
        GoogleTokenProvider tokens = mock(GoogleTokenProvider.class);
        GoogleDriveConnector connector = twoAccounts(tokens);
        when(tokens.accessToken("b@gmail.com")).thenReturn("token-b");
        assertThat(connector.listAllFiles("gmail.com")).extracting(WorkspaceFile::name)
                .containsExactlyInAnyOrder("top.txt", "marks.txt", "second.txt");
    }

    @Test
    void aFileIsFoundThroughTheAccountThatCanOpenIt() {
        GoogleTokenProvider tokens = mock(GoogleTokenProvider.class);
        GoogleDriveConnector connector = twoAccounts(tokens);
        when(tokens.accessToken("b@gmail.com")).thenReturn("token-b");
        assertThat(connector.getFile("gmail.com", "C")).get().extracting(WorkspaceFile::name).isEqualTo("second.txt");
    }

    @Test
    void anAccountThatHasNotSignedInStopsTheWholeCheck() {
        GoogleTokenProvider tokens = mock(GoogleTokenProvider.class);
        GoogleDriveConnector connector = twoAccounts(tokens);
        when(tokens.accessToken("b@gmail.com")).thenThrow(new IllegalStateException("b@gmail.com has not signed in"));
        assertThatThrownBy(() -> connector.listAllFiles("gmail.com")).hasMessageContaining("has not signed in");
    }

    @Test
    void rootMeansTheWholeMyDriveIncludingSubfolders() {
        List<WorkspaceFile> files = connector("root", 10).listAllFiles("gmail.com");
        assertThat(files).extracting(WorkspaceFile::name).containsExactlyInAnyOrder("top.txt", "marks.txt");
        assertThat(files).filteredOn(f -> f.name().equals("marks.txt")).extracting(WorkspaceFile::folderPath)
                .containsExactly("Student Records");
    }

    @Test
    void aFileIsFoundAgainWhenScanningTheWholeDrive() {
        // the re-check after "I've fixed it": if this came back empty the file would be recorded as deleted
        assertThat(connector("root", 10).getFile("gmail.com", "B")).get()
                .extracting(WorkspaceFile::folderPath).isEqualTo("Student Records");
    }

    @Test
    void aDriveOverTheLimitFailsInsteadOfReturningPartOfTheFiles() {
        assertThatThrownBy(() -> connector("root", 1).listAllFiles("gmail.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("more than 1 files");
    }

    @Test
    void aFolderIdStillLimitsTheScanToThatFolder() {
        assertThat(connector("F1", 10).listAllFiles("gmail.com")).extracting(WorkspaceFile::name).containsExactly("marks.txt");
    }
}
