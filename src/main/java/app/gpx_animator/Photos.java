/*
 *  Copyright 2019 Marcus Fihlon, Switzerland
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package app.gpx_animator;

import app.gpx_animator.frameWriter.FrameWriter;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NonNls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@SuppressWarnings("PMD.BeanMembersShouldSerialize") // This class is not serializable
public final class Photos {

    @NonNls
    private static final Logger LOGGER = LoggerFactory.getLogger(Photos.class);

    private static final String SYSTEM_ZONE_OFFSET;

    static {
        final ZonedDateTime dateTime = ZonedDateTime.now();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("x"); //NON-NLS
        SYSTEM_ZONE_OFFSET = dateTime.format(formatter);
    }

    private final ResourceBundle resourceBundle = Preferences.getResourceBundle();

    private final Map<Long, List<Photo>> allPhotos;

    public Photos(final String dirname) {
        if (dirname == null || dirname.isBlank()) {
            allPhotos = new HashMap<>();
        } else {
            final File directory = new File(dirname);
            if (directory.isDirectory()) {
                final File[] files = directory.listFiles((dir, name) -> {
                    final String lowerCaseName = name.toLowerCase(Locale.getDefault());
                    return lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg") || lowerCaseName.endsWith(".png"); //NON-NLS
                });
                if (files != null) {
                    allPhotos = Arrays.stream(files).map(this::toPhoto).filter(photo -> photo.getEpochSeconds() > 0)
                            .collect(groupingBy(Photo::getEpochSeconds));
                } else {
                    allPhotos = new HashMap<>();
                }
            } else {
                LOGGER.error("'{}' is not a directory!", directory);
                allPhotos = new HashMap<>();
            }
        }
    }

    private Photo toPhoto(final File file) {
        return new Photo(timeOfPhotoInMilliSeconds(file), file);
    }

    private Long timeOfPhotoInMilliSeconds(final File file) {
        try {
            final Metadata metadata = ImageMetadataReader.readMetadata(file);
            final ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            final String zoneOffset = directory.getString(36881) != null ? directory.getString(36881) : SYSTEM_ZONE_OFFSET;
            final String dateTimeString = directory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                    .concat(" ").concat(zoneOffset.replace(":", ""));
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeString,
                    DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss x")); //NON-NLS
            return zonedDateTime.toEpochSecond() * 1_000;
        } catch (ImageProcessingException | IOException | NullPointerException e) { // NOPMD -- NPEs can happen quite often in image metadata handling
            LOGGER.error("Error processing file '{}}'!", file.getAbsoluteFile(), e);
            return 0L;
        }
    }

    public ArrayList<BufferedImage> createFadedInPhoto(final BufferedImage bi2, final BufferedImage photoImage, final Graphics2D g2d,
                                                       final int posX, final int posY, final long numIncreasingFrames) {

            int scaleX = (int) ((photoImage.getWidth() - 10) / numIncreasingFrames);
            int scaleY = (int) ((photoImage.getHeight() - 10) / numIncreasingFrames);
            int accX = 10;
            int accY = 10;
            ArrayList<BufferedImage> increasingBI = new ArrayList<>();

            for (long frame = 0; frame < numIncreasingFrames; frame++) {
                g2d.drawImage(photoImage.getScaledInstance(accX, accY, photoImage.SCALE_FAST), posX, posY, null);
                increasingBI.add(Utils.deepCopy(bi2));
                accX += scaleX;
                accY += scaleY;
            }
            return increasingBI;
    }

    private void renderPhoto(final Photo photo, final Configuration cfg,
                                    final BufferedImage bi, final FrameWriter frameWriter,
                                    final RenderingContext rc, final int pct) {
        rc.setProgress1(pct, String.format(resourceBundle.getString("photos.progress.rendering"), photo.getFile().getName()));

        final BufferedImage photoImage = readPhoto(photo, bi.getWidth(), bi.getHeight());
        if (photoImage != null) {
            final BufferedImage bi2 = Utils.deepCopy(bi);
            final Graphics2D g2d = bi2.createGraphics();
            final int posX = (bi.getWidth() - photoImage.getWidth()) / 2;
            final int posY = (bi.getHeight() - photoImage.getHeight()) / 2;

            final long ms = cfg.getPhotoTime();
            final long fps = Double.valueOf(cfg.getFps()).longValue();
            final long frames = ms * fps / 1_000;

            // fade in photo if frames >= 90
            // for fps/2 increase image size from 10x10 to full size. I.e., fade in/out in 0.5 seconds

            // add on (x) each time
            int minMs = 3000;


            if (ms >= minMs) {

                 try {
                    ArrayList<BufferedImage> increasingPhotoFrames = new ArrayList<BufferedImage>();
                    final long numIncreasingFrames = (fps / 2);

                    increasingPhotoFrames = createFadedInPhoto(bi2, photoImage, g2d, posX, posY, numIncreasingFrames);

                    // fade in photo
                    for (int frame = 0; frame < increasingPhotoFrames.size(); frame++) {
                        frameWriter.addFrame(increasingPhotoFrames.get(frame));
                    }

                    // normal size for middle section totalFrames - (fps/2)*2)
                    // figure how to draw normal photoimage to bi2 full size
                    for (int frame = 0; frame < (frames - fps); frame++) {
                        frameWriter.addFrame(bi2);
                    }

                    // fade out  photo
                    for (int frame = increasingPhotoFrames.size() - 1; frame >= 0; frame--) {
                         frameWriter.addFrame(increasingPhotoFrames.get(frame));
                    }

                } catch (final UserException e) {
                    LOGGER.error("Problems rendering photo '{}'!", photo, e);
                }


            } else {
                try {
                    g2d.drawImage(photoImage, posX, posY, null);
                    for (long frame = 0; frame < frames; frame++) {
                        frameWriter.addFrame(bi2);
                    }
               } catch (final UserException e) {
                    LOGGER.error("Problems rendering photo '{}'!", photo, e);
                }
            }
        }
    }

    private BufferedImage readPhoto(final Photo photo, final int width, final int height) {
        try {
            final BufferedImage image = ImageIO.read(photo.getFile());
            final int scaledWidth = Math.round(width * 0.7f);
            final int scaledHeight = Math.round(height * 0.7f);
            final BufferedImage scaledImage = scaleImage(image, scaledWidth, scaledHeight);
            final BufferedImage borderedImage = addBorder(scaledImage);
            borderedImage.flush();
            return borderedImage;
        } catch (final IOException e) {
            LOGGER.error("Problems reading photo '{}'!", photo, e);
        }
        return null;
    }

    private static BufferedImage addBorder(final BufferedImage image) {
        int borderWidth = image.getWidth() / 15;
        int borderHeight = image.getHeight() / 15;
        int borderSize = Math.min(borderWidth, borderHeight);
        int outerBorderSize = borderSize / 5;
        final BufferedImage border = new BufferedImage(
                image.getWidth() + 2 * borderSize,
                image.getHeight() + 2 * borderSize,
                image.getType());

        final Graphics2D g2d = border.createGraphics();
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(0, 0, border.getWidth(), border.getHeight());
        g2d.setColor(Color.WHITE);
        g2d.fillRect(outerBorderSize, outerBorderSize,
                border.getWidth() - (2 * outerBorderSize), border.getHeight() - (2 * outerBorderSize));
        g2d.drawImage(image, borderSize, borderSize, null);
        g2d.dispose();

        return border;
    }

    private static BufferedImage scaleImage(final BufferedImage image, final int width, final int height) {
        return Scalr.resize(Scalr.resize(image,
                Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_WIDTH, width),
                Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_HEIGHT, height);
    }

    public void render(final Long gpsTime, final Configuration cfg, final BufferedImage bi,
                       final FrameWriter frameWriter, final RenderingContext rc, final int pct) {
        final List<Long> keys = allPhotos.keySet().stream()
                .filter(photoTime -> gpsTime >= photoTime)
                .collect(Collectors.toList());
        if (!keys.isEmpty()) {
            keys.stream()
                    .map(allPhotos::get)
                    .flatMap(List::stream).collect(Collectors.toList())
                    .forEach(photo -> renderPhoto(photo, cfg, bi, frameWriter, rc, pct));
            keys.forEach(allPhotos::remove);
        }
    }
}
