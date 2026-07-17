package com.example.ingest.gateway.ftp;

import com.example.ingest.gateway.GatewayProperties;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Classic-FTP implementation of {@link FileSourceClient} (design decision
 * D2). Passive mode, binary type, one connection per operation. Known
 * trade-off: FTP rename atomicity is server-dependent — acceptable because a
 * double-claim only causes a duplicate publish, which Nats-Msg-Id and the
 * worker ledger both dedup. Switch to FTPS by swapping FTPClient for
 * FTPSClient; SFTP/local-mount are separate implementations of the SPI.
 */
@Component
@Profile("gateway")
@ConditionalOnProperty(prefix = "app.gateway.ftp", name = "enabled", havingValue = "true")
public class FtpFileSourceClient implements FileSourceClient {

    private final GatewayProperties.Ftp config;

    public FtpFileSourceClient(GatewayProperties properties) {
        this.config = properties.ftp();
    }

    @FunctionalInterface
    private interface FtpAction<T> {
        T apply(FTPClient ftp) throws IOException;
    }

    private <T> T withClient(FtpAction<T> action) throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        ftp.setDataTimeout(Duration.ofSeconds(30));
        ftp.setControlEncoding("UTF-8");
        try {
            ftp.connect(config.host(), config.port());
            if (!ftp.login(config.username(), config.password())) {
                throw new IOException("FTP login failed for " + config.username());
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            return action.apply(ftp);
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.logout();
                } catch (IOException ignored) {
                    // best-effort logout; disconnect below is what matters
                }
                ftp.disconnect();
            }
        }
    }

    @Override
    public List<RemoteFile> listInbox() throws IOException {
        return withClient(ftp -> Arrays.stream(ftp.listFiles(config.inboxDir()))
                .filter(FTPFile::isFile)
                .map(file -> new RemoteFile(file.getName(),
                        file.getTimestamp() == null ? Instant.EPOCH : file.getTimestamp().toInstant()))
                .toList());
    }

    @Override
    public boolean claim(RemoteFile file) throws IOException {
        return withClient(ftp -> ftp.rename(
                path(config.inboxDir(), file.name()), path(config.processingDir(), file.name())));
    }

    @Override
    public InputStream read(RemoteFile file) throws IOException {
        return withClient(ftp -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ftp.retrieveFile(path(config.processingDir(), file.name()), out)) {
                throw new IOException("failed to retrieve " + file.name());
            }
            return new ByteArrayInputStream(out.toByteArray());
        });
    }

    @Override
    public void archive(RemoteFile file) throws IOException {
        move(file, config.archiveDir());
    }

    @Override
    public void error(RemoteFile file) throws IOException {
        move(file, config.errorDir());
    }

    private void move(RemoteFile file, String targetDir) throws IOException {
        withClient(ftp -> {
            if (!ftp.rename(path(config.processingDir(), file.name()), path(targetDir, file.name()))) {
                throw new IOException("failed to move " + file.name() + " to " + targetDir);
            }
            return null;
        });
    }

    private static String path(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }
}
