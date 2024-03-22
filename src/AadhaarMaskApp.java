
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;

public class AadhaarMaskApp extends JFrame {

	private File selectedFile;
	private JTextArea textArea;
	private BufferedImage originalImage;
	private BufferedImage maskedImage;
	private JPanel mainPanel;

	public AadhaarMaskApp() {
		setTitle("Aadhaar Masking App");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());

		textArea = new JTextArea(5, 30);
		textArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(textArea);

		JPanel buttonPanel = new JPanel();
		JButton uploadButton = new JButton("Upload a File");
		uploadButton.addActionListener(new UploadButtonListener());
		JButton downloadButton = new JButton("Download Masked Image");
		downloadButton.addActionListener(new DownloadButtonListener());
		buttonPanel.add(uploadButton);
		buttonPanel.add(downloadButton);

		mainPanel.add(scrollPane, BorderLayout.NORTH);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		add(mainPanel);

		// Load OpenCV library
//		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private class UploadButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser fileChooser = new JFileChooser();
			int result = fileChooser.showOpenDialog(AadhaarMaskApp.this);
			if (result == JFileChooser.APPROVE_OPTION) {
				selectedFile = fileChooser.getSelectedFile();
				textArea.append("File Selected: " + selectedFile.getName() + "\n");

				// Load the original image and display it
				try {
					originalImage = ImageIO.read(selectedFile);
					displayImage(originalImage);

					// Perform OCR to get Aadhaar number
					String aadhaarNumber = performOCR(originalImage);
					if (aadhaarNumber != null) {
						textArea.append("Extracted Aadhaar Number: " + aadhaarNumber + "\n");

						// Convert BufferedImage to OpenCV Mat
						Mat matImage = bufferedImageToMat(originalImage);

						// Perform OCR to get Aadhaar number position
						Rect aadhaarRect = performOcrForPosition(matImage, aadhaarNumber);
						System.out.println("aadhaarRect=" + aadhaarRect);
						if (aadhaarRect != null) {
							// Mask Aadhaar number
							originalImage = maskAadhaarNumber(originalImage, aadhaarRect);

							// Display the masked image on the canvas
							displayImage(originalImage);
						} else {
							textArea.append("Error: Aadhaar number position not found.\n");
						}
					} else {
						textArea.append("Error: Aadhaar number not found or invalid.\n");
					}

				} catch (IOException ex) {
					textArea.append("Error loading image.\n");
					ex.printStackTrace();
				}
			}
		}
	}

	private class DownloadButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if (originalImage == null) {
				textArea.append("Error: No image selected.\n");
				return;
			}

			// Perform OCR to get Aadhaar number
			String aadhaarNumber = performOCR(originalImage);

			if (aadhaarNumber == null || aadhaarNumber.length() < 8) {
				textArea.append("Error: Aadhaar number not found or invalid.\n");
				return;
			}

			// Convert BufferedImage to OpenCV Mat
			Mat matImage = bufferedImageToMat(originalImage);

			// Perform OCR to get Aadhaar number position
			Rect aadhaarRect = performOcrForPosition(matImage, aadhaarNumber);

			if (aadhaarRect != null) {
				// Mask Aadhaar number
				originalImage = maskAadhaarNumber(originalImage, aadhaarRect);

				// Save the masked image to a file
				File outputMaskedFile = new File("C:\\AadharImg\\masked_aadhaar_image.png");
				try {
					ImageIO.write(originalImage, "png", outputMaskedFile);
					textArea.append("Masked Aadhaar image saved as 'masked_aadhaar_image.png'\n");

					// Display the masked image
					showMaskedImage(originalImage);
				} catch (IOException ex) {
					textArea.append("Error saving masked Aadhaar image.\n");
					ex.printStackTrace();
				}
			} else {
				textArea.append("Error: Aadhaar number not found on the image.\n");
			}
		}
	}

	private String performOCR(BufferedImage image) {
		String aadhaarNumber = null;
		ITesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:\\Users\\Bhaiyya Shaikh\\AppData\\Local\\Programs\\Tesseract-OCR\\tessdata");
		tesseract.setLanguage("eng");

		try {
			aadhaarNumber = tesseract.doOCR(image);
			aadhaarNumber = extractAadhaarNumber(aadhaarNumber);
			System.out.println("Extracted Aadhaar Number: " + aadhaarNumber);
		} catch (TesseractException ex) {
			textArea.append("Error performing OCR.\n");
			ex.printStackTrace();
		}

		return aadhaarNumber;
	}

	private String extractAadhaarNumber(String ocrResult) {
		String regexPattern = "\\b\\d{4} \\d{4} \\d{4}\\b";
		Pattern pattern = Pattern.compile(regexPattern);
		Matcher matcher = pattern.matcher(ocrResult);
		String aadharNumber = "";
		while (matcher.find()) {
			aadharNumber = matcher.group();
		}
		return aadharNumber;
	}


	private BufferedImage maskAadhaarNumber(BufferedImage image, Rect aadhaarRect) {
		// Convert BufferedImage to OpenCV Mat
		Mat matImage = bufferedImageToMat(image);

		// Calculate the starting point for the masking rectangle
		int startX = aadhaarRect.x;
		int startY = aadhaarRect.y;

		// Create a black rectangle to mask the Aadhaar number
		Rect maskRect = new Rect(startX, startY, aadhaarRect.width, aadhaarRect.height);
		Mat blackRect = new Mat(maskRect.size(), CvType.CV_8UC3, new Scalar(0, 0, 0));

		// Overlay the black rectangle onto the original image at the Aadhaar number's
		// position
		Mat roi = matImage.submat(maskRect);
		blackRect.copyTo(roi);

		// Convert the modified Mat back to BufferedImage
		return matToBufferedImage(matImage);
	}

	private Rect performOcrForPosition(Mat image, String aadhaarNumber) {
		ITesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:\\\\Users\\\\Bhaiyya Shaikh\\\\AppData\\\\Local\\\\Programs\\\\Tesseract-OCR\\\\tessdata");
		tesseract.setLanguage("eng");

		try {
			BufferedImage bufferedImage = matToBufferedImage(image);
			String ocrResult = tesseract.doOCR(bufferedImage);
			System.out.println("OCR Result: " + ocrResult); // Add this line for debugging

			List<Word> words = tesseract.getWords(bufferedImage, ITessAPI.TessPageIteratorLevel.RIL_WORD);
			
			for (Word word : words) {
				String wordText = word.getText().replaceAll("\\s+", "");; // Remove spaces from word text
				System.out.println("wordText  ==" + wordText);
System.out.println("aadhaarNumber  =="+aadhaarNumber);
				String FirstFour = aadhaarNumber.substring(0, 9);
				System.out.println("FirstFour ==" + FirstFour.length());
				if (FirstFour.equals(wordText)) {
					Rectangle boundingBox = word.getBoundingBox();
					System.out.println("boundingBox ==" + boundingBox.x);
					Rect openCVRect = new Rect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
					return openCVRect;
				}

				String SecondFour = aadhaarNumber.substring(5, 9);
				System.out.println("SecondFour  ==" + SecondFour.length());
				if (SecondFour.equals(wordText)) {
					Rectangle boundingBox = word.getBoundingBox();
					System.out.println("boundingBox ==" + boundingBox.x);
					Rect openCVRect = new Rect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
					return openCVRect;

				}

			}
		} catch (TesseractException ex) {
			textArea.append("Error performing OCR for position.\n");
			ex.printStackTrace();
		}

		return null;
	}

	private Mat bufferedImageToMat(BufferedImage image) {
		int type = image.getType();
		int numChannels = (type == BufferedImage.TYPE_3BYTE_BGR) ? 3 : 1;

		Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC(numChannels));
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		mat.put(0, 0, data);

		return mat;
	}

	private BufferedImage matToBufferedImage(Mat mat) {
		int type = BufferedImage.TYPE_BYTE_GRAY;
		if (mat.channels() > 1) {
			type = BufferedImage.TYPE_3BYTE_BGR;
		}

		int bufferSize = mat.channels() * mat.cols() * mat.rows();
		byte[] data = new byte[bufferSize];
		mat.get(0, 0, data);

		BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
		image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);

		return image;
	}

	private void displayImage(BufferedImage image) {
		int width = 600;
		int height = (int) ((double) width / image.getWidth() * image.getHeight());
		if (height > 400) {
			height = 400;
			width = (int) ((double) height / image.getHeight() * image.getWidth());
		}
		Image resizedImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		ImageIcon imageIcon = new ImageIcon(resizedImage);

		JLabel imageLabel = new JLabel(imageIcon);
		JPanel imagePanel = new JPanel();
		imagePanel.add(imageLabel);

		mainPanel.add(imagePanel, BorderLayout.CENTER);
		mainPanel.revalidate();
	}

	private void showMaskedImage(BufferedImage image) {
		JFrame maskedImageFrame = new JFrame("Masked Aadhaar Image");
		maskedImageFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		maskedImageFrame.setSize(800, 600);
		maskedImageFrame.setLocationRelativeTo(null);

		JLabel maskedImageLabel = new JLabel(new ImageIcon(image));
		maskedImageLabel.setHorizontalAlignment(JLabel.CENTER);

		maskedImageFrame.add(maskedImageLabel);
		maskedImageFrame.setVisible(true);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			AadhaarMaskApp app = new AadhaarMaskApp();
			app.setVisible(true);
		});
	}
}
