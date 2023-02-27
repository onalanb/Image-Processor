// Baran Onalan
// CPSC 5200
// 3/3/2023
// Professor Daugherty

package com.onalan.ip;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Path("/image")
public class ImageResource {

    @POST
    @Path("/process")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("image/jpeg")
    // curl -i -F "file=@Azuki.jpg" http://localhost:8080/IP-1.0-SNAPSHOT/api/image/process?command=rotate_30:rotate_right:grayscale:resize_300_600:thumbnail:flip_vertical --output Azuki_FinalForm.jpg
    // This API contains the little language I created in order to make multiple command calls simultaneously.
    public Response process(@FormDataParam("file") InputStream uploadedInputStream,
                            @FormDataParam("file") FormDataContentDisposition fileDetail,
                            @QueryParam("command") String commandString) {
        String fileName = fileDetail.getFileName();
        System.out.println("fileName = " + fileName);
        String[] fileNameParts = fileDetail.getFileName().split("\\.");
        String fileFormat = fileNameParts[fileNameParts.length - 1];

        // When error code is 200, it means there is no error.
        int errorCode = 200;
        String errorMessage = "";

        byte[] imageData = null;
        // All commands are split by ":"
        String[] commands = commandString.split(":");
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i];
            // All command details are separated by "_"
            String[] inputs = command.split("_");
            if (inputs.length == 0) continue;
            String operation = inputs[0];
            if (imageData != null) {
                // First iteration will enter as an Input Stream, every other iteration should be a Byte[]
                uploadedInputStream = new ByteArrayInputStream(imageData);
            }
            if (operation.equalsIgnoreCase("flip")) {
                if (inputs.length == 2) {
                    String direction = inputs[1];
                    // flip command expects a horizontal or vertical input command
                    if (direction.equalsIgnoreCase("horizontal") ||
                        direction.equalsIgnoreCase("vertical")) {
                        imageData = flip(uploadedInputStream, fileFormat, direction);
                    } else {
                        errorCode = 400;
                        errorMessage = "Flip command expects 'horizontal' or 'vertical' as argument.";
                    }
                } else {
                    errorCode = 411;
                    errorMessage = "Flip command expects one argument.";
                }
            } else if (operation.equalsIgnoreCase("rotate")) {
                if (inputs.length == 2) {
                    String direction = inputs[1];
                    int degrees;
                    if (direction.equalsIgnoreCase("left")) {
                        degrees = -90;
                    } else if (direction.equalsIgnoreCase("right")) {
                        degrees = 90;
                    } else {
                        try {
                            // rotate command expects a left right or value input command
                            degrees = Integer.parseInt(direction);
                            imageData = rotate(uploadedInputStream, fileFormat, degrees);
                        } catch (NumberFormatException invalidFormat) {
                            errorCode = 400;
                            errorMessage = "Rotate command expects 'left' or 'right' or an integer degree value.";
                        }
                    }
                } else {
                    errorCode = 411;
                    errorMessage = "Rotate command expects one argument.";
                }
            } else if (operation.equalsIgnoreCase("grayscale")) {
                if (inputs.length > 1) {
                    errorCode = 411;
                    errorMessage = "Grayscale command expects no arguments.";
                }
                // grayscale command doesn't have any other specifications, it just grayscales the image.
                imageData = grayscale(uploadedInputStream, fileFormat);
            } else if (operation.equalsIgnoreCase("resize")) {
                if (inputs.length == 3) {
                    try {
                        // resize command expects a new width and height input
                        int width = Integer.parseInt(inputs[1]);
                        int height = Integer.parseInt(inputs[2]);
                        if (width < 1 || height < 1) {
                            errorCode = 400;
                            errorMessage = "Resize command expects an int width and int length that are both positive.";
                        }
                        imageData = resize(uploadedInputStream, fileFormat, width, height);
                    } catch (NumberFormatException invalidInput) {
                        errorCode = 400;
                        errorMessage = "Resize command expects an int width and int length that are both positive.";
                    }
                } else {
                    errorCode = 411;
                    errorMessage = "Resize command expects two arguments.";
                }
            } else if (operation.equalsIgnoreCase("thumbnail")) {
                if (inputs.length > 1) {
                    errorCode = 411;
                    errorMessage = "Thumbnail command expects no arguments.";
                }
                // thumbnail command doesn't have any other specifications, it just thumbnails the image.
                imageData = thumbnail(uploadedInputStream, fileFormat);
            } else {
                // Command not allowed.
                errorCode = 405;
                errorMessage = "The only valid commands are: Flip, Rotate, Resize, Grayscale, Thumbnail";
            }
        }

        if (errorCode == 200) {
            // Byte Array to HTTP Response
            return Response.ok(imageData).build();
        } else {
            return Response.status(errorCode).entity(errorMessage).type("text/plain").build();
        }
    }

    // This method rotates the image any number of degrees in either direction, this is used by the process API.
    private byte[] rotate(InputStream uploadedInputStream,
                           String fileFormat,
                           int degrees) {
        BufferedImage buffImg = null;
        byte[] imageData = null;
        try {
            buffImg = ImageIO.read(uploadedInputStream);

            final double rads = Math.toRadians(degrees);
            final double sin = Math.abs(Math.sin(rads));
            final double cos = Math.abs(Math.cos(rads));
            final int w = (int) Math.floor(buffImg.getWidth() * cos + buffImg.getHeight() * sin);
            final int h = (int) Math.floor(buffImg.getHeight() * cos + buffImg.getWidth() * sin);
            final BufferedImage rotatedImage = new BufferedImage(w, h, buffImg.getType());
            final AffineTransform at = new AffineTransform();
            at.translate(w / 2, h / 2);
            at.rotate(rads,0, 0);
            at.translate(-buffImg.getWidth() / 2, -buffImg.getHeight() / 2);
            final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            rotateOp.filter(buffImg, rotatedImage);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rotatedImage, fileFormat, baos);
            imageData = baos.toByteArray();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return imageData;
    }

    // This method flips the image horizontally or vertically, this is used by the process API.
    private byte[] flip(InputStream uploadedInputStream,
                        String fileFormat,
                        String direction) {
        BufferedImage buffImg = null;
        byte[] imageData = null;
        try {
            buffImg = ImageIO.read(uploadedInputStream);

            AffineTransform tx = null;
            if(direction.equalsIgnoreCase("horizontal")) {
                tx = AffineTransform.getScaleInstance(-1, 1);
                tx.translate(-buffImg.getWidth(), 0);
            } else if(direction.equalsIgnoreCase("vertical")) {
                tx = AffineTransform.getScaleInstance(1, -1);
                tx.translate(0, -buffImg.getHeight());
            }
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            buffImg = op.filter(buffImg, null);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(buffImg, fileFormat, baos);
            imageData = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageData;
    }

    // This method resizes the image to any width and height, this is used by the process API.
    private byte[] resize(InputStream uploadedInputStream,
                          String fileFormat,
                          int width,
                          int height) {
        BufferedImage buffImg = null;
        byte[] imageData = null;
        try {
            buffImg = ImageIO.read(uploadedInputStream);
            BufferedImage resizedImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = resizedImg.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(buffImg, 0, 0, width, height, null);
            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImg, fileFormat, baos);
            imageData = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageData;
    }

    // This method grayscales the image, this is used by the process API.
    private byte[] grayscale(InputStream uploadedInputStream,
                             String fileFormat) {
        BufferedImage buffImg = null;
        byte[] imageData = null;
        try {
            buffImg = ImageIO.read(uploadedInputStream);
            BufferedImage grayImg = new BufferedImage(buffImg.getWidth(), buffImg.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2 = grayImg.createGraphics();
            g2.drawImage(buffImg, 0, 0, null);
            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(grayImg, fileFormat, baos);
            imageData = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageData;
    }

    // This method thumbnails the image, this is used by the process API.
    private byte[] thumbnail(InputStream uploadedInputStream,
                             String fileFormat) {
        BufferedImage buffImg = null;
        byte[] imageData = null;
        try {
            buffImg = ImageIO.read(uploadedInputStream);
            int width = buffImg.getWidth();
            int height = buffImg.getHeight();
            int size = (height >= width) ? height : width;

            // padding into a square
            final BufferedImage paddedImage = new BufferedImage(size, size, buffImg.getType());
            final AffineTransform at = new AffineTransform();
            if (width >= height) {
                at.translate(0, (width - height) / 2);
            } else {
                at.translate((height - width) / 2, 0);
            }
            final AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            op.filter(buffImg, paddedImage);

            // resizing to 300x300
            BufferedImage resizedImg = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = resizedImg.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(paddedImage, 0, 0, 300, 300, null);
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImg, fileFormat, baos);
            imageData = baos.toByteArray();
        } catch (IOException e) {
        e.printStackTrace();
        }
        return imageData;
    }
}
