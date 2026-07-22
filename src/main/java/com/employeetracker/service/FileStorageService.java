package com.employeetracker.service;

import com.employeetracker.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists uploaded employee profile photos to disk instead of storing
 * Base64 image data in the Users.PhotoUrl column (which is a VARCHAR(500)
 * and was overflowing with "Data truncation" errors for any real photo).
 *
 * Only the resulting relative URL (e.g. /uploads/profile-photos/<uuid>.jpg)
 * is ever persisted to the database.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // Matches e.g. "data:image/png;base64,iVBORw0KGgoAAAANS..."
    private static final Pattern DATA_URL_PATTERN =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    private final Path uploadRoot;
    private final String profilePhotoSubdir;
    private final long maxPhotoSizeBytes;
    private final List<String> allowedPhotoTypes;

    public FileStorageService(
            @Value("${app.upload.root-dir:uploads}") String uploadRootDir,
            @Value("${app.upload.profile-photo-subdir:profile-photos}") String profilePhotoSubdir,
            @Value("${app.upload.max-photo-size-bytes:2097152}") long maxPhotoSizeBytes,
            @Value("${app.upload.allowed-photo-types:image/jpeg,image/png,image/gif,image/webp}") String allowedPhotoTypesCsv
    ) {
        this.uploadRoot = Paths.get(uploadRootDir).toAbsolutePath().normalize();
        this.profilePhotoSubdir = profilePhotoSubdir;
        this.maxPhotoSizeBytes = maxPhotoSizeBytes;
        this.allowedPhotoTypes = Arrays.stream(allowedPhotoTypesCsv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .toList();
    }

    /**
     * Resolves whatever the Add/Edit Employee form sent for "Profile Photo"
     * into the value that should actually be stored in Users.PhotoUrl.
     *
     * - null / blank -&gt; null (no photo - matches "store NULL" requirement)
     * - a new upload (a "data:image/...;base64,..." payload from the
     *   browser's FileReader) -&gt; validated, saved to disk, and the
     *   relative URL to the saved file is returned
     * - anything else (e.g. an already-saved relative URL like
     *   "/uploads/profile-photos/xxx.png", sent back unchanged by the Edit
     *   form when the admin didn't pick a new photo) -&gt; returned as-is,
     *   so editing an employee without touching the photo never re-saves
     *   or corrupts the existing file reference
     */
    public String resolvePhotoUrl(String photoValue) {
        if (photoValue == null || photoValue.isBlank()) {
            return null;
        }

        Matcher matcher = DATA_URL_PATTERN.matcher(photoValue.trim());
        if (!matcher.matches()) {
            // Not a new Base64 upload (most likely an existing stored path
            // being echoed back unchanged) - leave it untouched.
            return photoValue;
        }

        String mimeType = matcher.group(1).toLowerCase();
        String base64Payload = matcher.group(2);

        if (!allowedPhotoTypes.contains(mimeType)) {
            throw new BadRequestException(
                    "Unsupported profile photo type: " + mimeType + ". Allowed types: " + String.join(", ", allowedPhotoTypes));
        }

        byte[] fileBytes;
        try {
            fileBytes = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Profile photo could not be read - the uploaded file appears to be corrupted");
        }

        if (fileBytes.length == 0) {
            throw new BadRequestException("Profile photo file is empty");
        }

        if (fileBytes.length > maxPhotoSizeBytes) {
            throw new BadRequestException(
                    "Profile photo is too large (" + (fileBytes.length / 1024) + " KB). Maximum allowed size is "
                            + (maxPhotoSizeBytes / 1024) + " KB");
        }

        String extension = extensionFor(mimeType);
        String filename = UUID.randomUUID() + extension;

        try {
            Path targetDir = uploadRoot.resolve(profilePhotoSubdir).normalize();
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(filename).normalize();
            if (!targetFile.startsWith(targetDir)) {
                // Defensive check - filename is a generated UUID so this can't
                // actually happen, but never write outside the intended folder.
                throw new BadRequestException("Invalid profile photo filename");
            }

            Files.write(targetFile, fileBytes);
        } catch (IOException ex) {
            log.error("Failed to save uploaded profile photo to disk", ex);
            throw new BadRequestException("Unable to save profile photo. Please try again.");
        }

        return "/uploads/" + profilePhotoSubdir + "/" + filename;
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
