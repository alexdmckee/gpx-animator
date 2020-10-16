package app.gpx_animator;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class PhotosTest {


    @Test
    void createFadedInPhoto() {

        Photos testee = new Photos(null);


        int width = 100;
        int height = 100;
        int numIncreasingFrames = 40;

        // private photos method readPhoto ensures the photo is scaled to fit in the image its being drawn on
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage photoImage = new BufferedImage(70, 70, BufferedImage.TYPE_INT_RGB);


        final Graphics2D g2d = bi.createGraphics();
        final int posX = (bi.getWidth() - photoImage.getWidth()) / 2;
        final int posY = (bi.getHeight() - photoImage.getHeight()) / 2;

        ArrayList<BufferedImage> increasingPhoto = testee.createFadedInPhoto(bi, photoImage, g2d, posX, posY, numIncreasingFrames);

        // Assert we have created the correct number of frames
        assertEquals(numIncreasingFrames, increasingPhoto.size());

        // Assert that two frames in the increasing photo series are different. That the photo has infact increased
        assertNotEquals(increasingPhoto.get(0), increasingPhoto.get(1));

    }
}
