package dex.util;

import org.apache.commons.lang3.Validate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils
{
    public static InputStream toPngInputStream(final BufferedImage image) throws IOException
    {
        return toInputStream(image, "png");
    }

    public static InputStream toInputStream(final BufferedImage image, final String format) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, format, os);
        return new ByteArrayInputStream(os.toByteArray());
    }

    public static BufferedImage colorImage(final BufferedImage image, final Color color) {
        int width = image.getWidth();
        int height = image.getHeight();
        WritableRaster raster = image.getRaster();

        for (int xx = 0; xx < width; xx++) {
            for (int yy = 0; yy < height; yy++) {
                int[] pixels = raster.getPixel(xx, yy, (int[]) null);
                pixels[0] = color.getRed();
                pixels[1] = color.getGreen();
                pixels[2] = color.getBlue();
                raster.setPixel(xx, yy, pixels);
            }
        }
        return image;
    }

    public static BufferedImage combine(final java.util.List<BufferedImage> images)
    {
        Validate.notEmpty(images, "Can't combine an empty list of images!");

        // Validate heights and widths are all the same
        final int height = images.get(0).getHeight();
        final int individualWidth = images.get(0).getWidth();
        Validate.isTrue(images.stream()
                        .allMatch(image -> image.getWidth() == individualWidth && image.getHeight() == height),
                "All images should share the same height and width!");

        // Create a new image with the right dimensions
        final int width = images.stream()
                .mapToInt(BufferedImage::getWidth)
                .sum();
        // http://stackoverflow.com/questions/2318020/merging-two-images
        final BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Copy images into the new image
        final Graphics graphics = combined.createGraphics();
        int xOffset = 0;
        for (final BufferedImage image : images) {
            graphics.drawImage(image, xOffset, 0, null);
            xOffset += image.getWidth();
        }
        graphics.dispose();

        return combined;
    }
}
