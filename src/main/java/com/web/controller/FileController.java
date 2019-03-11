package com.web.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import com.web.communication.ExportRequest;
import com.web.communication.SegmentationRequest;
import com.web.config.FileConfiguration;
import com.web.facade.LarexFacade;
import com.web.model.BookSettings;
import com.web.model.PageSegmentation;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class FileController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FileManager fileManager;
	@Autowired
	private FileConfiguration config;

	@RequestMapping(value = "/images/books/{book}/{image}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getImage(@PathVariable("book") final String book,
			@PathVariable("image") final String image,
			@RequestParam(value = "resize", defaultValue = "false") boolean doResize) throws IOException {
		try {
			// Find file with image name
			init();
			File directory = new File(fileManager.getLocalBooksPath() + File.separator + book);
			File[] matchingFiles = directory.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith(image) && (name.endsWith("png") || name.endsWith("jpg")
							|| name.endsWith("jpeg") || name.endsWith("tif") || name.endsWith("tiff"));
				}
			});

			if (matchingFiles.length == 0)
				throw new IOException("File does not exist");

			byte[] imageBytes = null;

			File imageFile = matchingFiles[0];

			if (doResize) {
				// load Mat
				Mat imageMat = Imgcodecs.imread(imageFile.getAbsolutePath());
				// resize
				Mat resizeImage = new Mat();
				int width = 300;
				int height = (int) (imageMat.rows() * ((width * 1.0) / imageMat.cols()));
				Size sz = new Size(width, height);
				Imgproc.resize(imageMat, resizeImage, sz);
				imageMat.release();
				imageMat = resizeImage;

				// Convert to png
				BufferedImage bufferedImage = convertMatToBufferedImage(imageMat);
				try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream()) {
					ImageIO.write(bufferedImage, "png", byteArrayOut);
					imageBytes = byteArrayOut.toByteArray();
				}

				// Remove Garbage
				imageMat.release();
				System.gc();
			} else {
				if (imageFile.getName().endsWith("tif") || imageFile.getName().endsWith("tiff")) {
					// load Mat
					Mat imageMat = Imgcodecs.imread(imageFile.getAbsolutePath());

					// Convert to png
					BufferedImage bufferedImage = convertMatToBufferedImage(imageMat);
					try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream()) {
						ImageIO.write(bufferedImage, "png", byteArrayOut);
						imageBytes = byteArrayOut.toByteArray();
					}

					// Remove Garbage
					imageMat.release();
				} else
					imageBytes = Files.readAllBytes(imageFile.toPath());
			}

			// Create header to display the image
			HttpHeaders headers = new HttpHeaders();

			headers.setLastModified(imageFile.lastModified());
			headers.setCacheControl("no-cache");
			headers.setContentType(MediaType.IMAGE_PNG);
			headers.setContentLength(imageBytes.length);

			return new ResponseEntity<byte[]>(imageBytes, headers, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/uploadSegmentation", method = RequestMethod.POST)
	public @ResponseBody PageSegmentation uploadSegmentation(@RequestParam("file") MultipartFile file,
			@RequestParam("pageNr") int pageNr, @RequestParam("bookID") int bookID) {
		PageSegmentation result = null;
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				result = LarexFacade.readPageXML(bytes, pageNr, bookID, fileManager);
			} catch (Exception e) {
			}
		}
		return result;
	}

	@RequestMapping(value = "/exportXML", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestBody ExportRequest request) {
		try {
			init();
			final Document pageXML = LarexFacade.getPageXML(request.getSegmentation(), request.getVersion());

			switch (config.getSetting("localsave")) {
			case "bookpath":
				LarexFacade.savePageXMLLocal(
						fileManager.getLocalBooksPath() + File.separator
								+ LarexFacade.getBook(request.getBookid(), fileManager).getName(),
						request.getSegmentation().getName(), pageXML);
				break;
			case "savedir":
				String savedir = config.getSetting("savedir");
				if (savedir != null && !savedir.equals("")) {
					LarexFacade.savePageXMLLocal(savedir, request.getSegmentation().getName(), pageXML);
				} else {
					System.err.println("Warning: Save dir is not set. File could not been saved.");
				}
				break;
			case "none":
			case "default":
			}
			return convertDocumentToByte(pageXML, request.getSegmentation().getFileName());
		} catch (Exception e) {
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/downloadSettings", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> downloadSettings(@RequestBody SegmentationRequest exportRequest) {
		try {
			return convertDocumentToByte(LarexFacade.getSettingsXML(exportRequest.getSettings()), "settings.xml");
		} catch (Exception e) {
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/uploadSettings", method = RequestMethod.POST)
	public @ResponseBody BookSettings uploadSettings(@RequestParam("file") MultipartFile file,
			@RequestParam("bookID") int bookID) {
		BookSettings settings = null;
		LarexFacade.getDefaultSettings(LarexFacade.getBook(bookID, fileManager));
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				settings = LarexFacade.readSettings(bytes, bookID, fileManager);
			} catch (Exception e) {
			}
		}

		return settings;
	}

	private void init() {
		if (!fileManager.isInit()) {
			fileManager.init(servletContext);
		}
		if (!config.isInitiated()) {
			config.read(new File(fileManager.getConfigurationFile()));
			String bookFolder = config.getSetting("bookpath");
			if (!bookFolder.equals("")) {
				fileManager.setLocalBooksPath(bookFolder);
			}
		}
	}

	private BufferedImage convertMatToBufferedImage(Mat imageMat) {
		BufferedImage bufferedImage = null;
		int imageHeight = imageMat.rows();
		int imageWidth = imageMat.cols();
		byte[] data = new byte[imageHeight * imageWidth * (int) imageMat.elemSize()];
		int type;
		imageMat.get(0, 0, data);

		if (imageMat.channels() == 1)
			type = BufferedImage.TYPE_BYTE_GRAY;
		else
			type = BufferedImage.TYPE_3BYTE_BGR;

		bufferedImage = new BufferedImage(imageWidth, imageHeight, type);

		bufferedImage.getRaster().setDataElements(0, 0, imageWidth, imageHeight, data);
		return bufferedImage;
	}

	private ResponseEntity<byte[]> convertDocumentToByte(Document document, String filename) {
		// convert document to bytes
		byte[] documentbytes = null;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();) {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			transformer.transform(new DOMSource(document), new StreamResult(out));
			documentbytes = out.toByteArray();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/xml"));
		headers.setContentDispositionFormData(filename, filename + ".xml");
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		return new ResponseEntity<byte[]>(documentbytes, headers, HttpStatus.OK);
	}
}