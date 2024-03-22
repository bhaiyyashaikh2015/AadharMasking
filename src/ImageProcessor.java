import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class ImageProcessor extends JFrame {

    private BufferedImage image;
    private BufferedImage maskImage;
    private Graphics2D maskGraphics;
    private JFileChooser fileChooser;
    private ITesseract tess;

    public ImageProcessor() {
        setTitle("Image Processor");
        setSize(1000, 1000);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize the canvas
        Canvas canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(800, 600));
        add(canvas, BorderLayout.CENTER);

        // Create buttons for uploading a file and generating the mask file
        JButton uploadButton = new JButton("Upload a File");
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleFileInputChange();
                canvas.repaint();
            }
        });

        JButton downloadButton = new JButton("Download Mask File");
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadMaskFile();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Initialize the file chooser
        fileChooser = new JFileChooser();

        // Initialize Tesseract OCR
        tess = new Tesseract();
        tess.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata"); // Set the path to the tessdata directory
    }

    private void handleFileInputChange() {
        int choice = fileChooser.showOpenDialog(this);

        if (choice == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                image = ImageIO.read(selectedFile);
                maskImageOnCanvas();
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading the image.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void downloadMaskFile() {
        if (maskImage != null) {
            try {
                File outputFile = new File("C:\\AadharImg\\mask_image.png"); // Replace with the desired output file path
                ImageIO.write(maskImage, "PNG", outputFile);
                JOptionPane.showMessageDialog(this, "File downloaded successfully!", "Download", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error while downloading the file.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

 // ...

    private void maskImageOnCanvas() {
        try {
            String imageBase64 = convertImageToBase64(image);
            BufferedImage rotatedImage = rotateImage(imageBase64, 45);
            String angleBase64 = convertImageToBase64(rotatedImage);

            List<MaskBlock> maskCoordinate = performOCR(angleBase64);
            // Modify the maskCoordinate as per your logic
            maskAadhaarNumbers(maskCoordinate);

            maskImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            maskGraphics = maskImage.createGraphics();
            maskGraphics.setColor(Color.RED);

            if (!maskCoordinate.isEmpty()) {
                int[] xPoints = new int[maskCoordinate.size()];
                int[] yPoints = new int[maskCoordinate.size()];

                for (int i = 0; i < maskCoordinate.size(); i++) {
                    xPoints[i] = maskCoordinate.get(i).x;
                    yPoints[i] = maskCoordinate.get(i).y;
                }

                maskGraphics.fillPolygon(xPoints, yPoints, maskCoordinate.size());
            }

            repaint();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error while processing the image.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ...

    private BufferedImage rotateImage(String base64Image, int angle) throws IOException {
        BufferedImage originalImage = convertBase64ToImage(base64Image);

        double radianAngle = Math.toRadians(angle);
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        int imageType = BufferedImage.TYPE_INT_ARGB; // Use a known image type, such as TYPE_INT_ARGB

        BufferedImage rotatedImage = new BufferedImage(width, height, imageType);

        AffineTransform transform = new AffineTransform();
        transform.rotate(radianAngle, width / 2.0, height / 2.0);

        Graphics2D g2d = rotatedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, transform, null);
        g2d.dispose();

        return rotatedImage;
    }



    private String convertImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", bos);
        byte[] imageBytes = bos.toByteArray();
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private BufferedImage convertBase64ToImage(String base64Image) throws IOException {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image.split(",")[1]);
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        return ImageIO.read(bis);
    }

    private List<MaskBlock> performOCR(String imageBase) {
        List<MaskBlock> maskArray = new ArrayList<>();
        try {
            // Perform OCR using Tesseract
            File tempImageFile = File.createTempFile("temp_image", ".png");
            byte[] imageBytes = Base64.getDecoder().decode(imageBase.split(",")[1]);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                BufferedImage bufferedImage = ImageIO.read(bis);
                ImageIO.write(bufferedImage, "png", tempImageFile);
            }

            String extractedText = tess.doOCR(tempImageFile);
System.out.println(extractedText);
            // Process the extracted text to extract coordinates
            // In this example, let's assume the extracted text contains
            // comma-separated pairs of x and y coordinates: "100,100 200,100 200,200 100,200"
            String[] coordinates = extractedText.split("\\s+");
            for (String coordinate : coordinates) {
            	System.out.println(coordinate);
                String[] xy = coordinate.split(",");
                if (xy.length == 2) {
                    int x = Integer.parseInt(xy[0]);
                    int y = Integer.parseInt(xy[1]);
                    maskArray.add(new MaskBlock(x, y));
                }
            }

            tempImageFile.delete(); // Clean up temp file
        } catch (TesseractException | IOException e) {
            e.printStackTrace();
        }
        return maskArray;
    }

//    private void maskAadharNumber(List<MaskBlock> maskCordinate) {
//        // Modify this method to mask only the 8-digit Aadhar number
//        // ...
//
//        // In this example, we'll just mask all the coordinates provided
//    	
//    	
//    	
//    	
//    	
//    	
//    	
//    }
    
    
    private void maskAadhaarNumbers(List<MaskBlock> maskCoordinate) {
        Graphics2D graphics = image.createGraphics();
        Color transparentBlack = new Color(0, 0, 0, 150);
        graphics.setColor(transparentBlack);

        for (MaskBlock maskBlock : maskCoordinate) {
            int x = maskBlock.x;
            int y = maskBlock.y;

            String extractedText = "9865 2608 2759"; // Extracted Aadhar number (modify this)
            // For example, extract the last 8 digits of the extracted text
            if (extractedText.length() >= 8) {
                extractedText = extractedText.substring(extractedText.length() - 8);
            }

            int width = extractedText.length() * 15; // Adjust the width based on the number of digits
            int height = 20; // Adjust the height of the rectangle as needed

            graphics.fillRect(x, y, width, height);
        }

        graphics.dispose();
    }


    private static class MaskBlock {
        private int x;
        private int y;

        public MaskBlock(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;

        if (image != null) {
            g2d.drawImage(image, 0, 0, this);
        }

        if (maskImage != null) {
            g2d.drawImage(maskImage, 0, 0, this);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ImageProcessor frame = new ImageProcessor();
            frame.setVisible(true);
        });
    }
}
