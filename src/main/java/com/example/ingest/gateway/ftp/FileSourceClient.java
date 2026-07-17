package com.example.ingest.gateway.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Protocol abstraction for the file source. The poller only talks to this
 * interface — adding SFTP/FTPS/local-mount later means one new implementation
 * plus config, nothing else (design decision D2).
 *
 * <p>Lifecycle: {@code inbox/ -> processing/ (claim = rename, acts as the
 * multi-instance lock) -> archive/ | error/}.
 */
public interface FileSourceClient {

    record RemoteFile(String name, Instant modifiedAt) {
    }

    List<RemoteFile> listInbox() throws IOException;

    /** Rename inbox/name -> processing/name; false when another instance won the race. */
    boolean claim(RemoteFile file) throws IOException;

    /** Contents of processing/name. */
    InputStream read(RemoteFile file) throws IOException;

    void archive(RemoteFile file) throws IOException;

    void error(RemoteFile file) throws IOException;
}
