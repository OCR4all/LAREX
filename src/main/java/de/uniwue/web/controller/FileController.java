package de.uniwue.web.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
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
import org.xml.sax.SAXException;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.web.communication.ExportRequest;
import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.facade.segmentation.LarexFacade;
import de.uniwue.web.facade.segmentation.SegmentationSettings;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.io.PageXMLReader;
import de.uniwue.web.io.PageXMLWriter;
import de.uniwue.web.model.PageAnnotations;

/**
 * Communication Controller to provide file contents 
 * and process save as well as export requests
 */
@Controller
@Scope("request")
public class FileController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FilePathManager fileManager;
	@Autowired
	private LarexConfiguration config;

	/**
	 * Initialize the controller by loading the fileManager and settings if not
	 * loaded already.
	 **/
	@PostConstruct
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
	
	/**
	 * Request an image of a book, by book name and image name.
	 * Use resize to get a downscaled preview image, with a width of 300px.
	 */
	@RequestMapping(value = "/images/books/{book}/{image}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getImage(@PathVariable("book") final String book,
			@PathVariable("image") final String image,
			@RequestParam(value = "resize", defaultValue = "false") boolean doResize) throws IOException {
		try {
			// Find file with image name
			final File directory = new File(fileManager.getLocalBooksPath() + File.separator + book);
			final String imageName = image.replace(".png", "");
			
			final File[] matchingFiles = directory.listFiles((File dir, String name) -> {
					final int extStart = name.lastIndexOf(".");
					if (extStart > 0 && name.substring(0, extStart).equals(imageName)) {
						final String extension = name.substring(extStart + 1);
						return Arrays.asList("png", "jpg", "jpeg", "tif", "tiff").contains(extension);
					} else {
						return false;
					}
				}
			);

			assert matchingFiles != null;
			if (matchingFiles.length == 0)
				throw new IOException("File does not exist");

			byte[] imageBytes = null;

			File imageFile = matchingFiles[0];

			if (doResize) {
				// load Mat
				final Mat imageMat = Imgcodecs.imread(imageFile.getAbsolutePath());
				// resize
				final Mat resizeImage = new Mat();
				int width = 300;
				int height = (int) (imageMat.rows() * ((width * 1.0) / imageMat.cols()));
				Size sz = new Size(width, height);
				Imgproc.resize(imageMat, resizeImage, sz);
				MemoryCleaner.clean(imageMat);

				// Convert to png
				BufferedImage bufferedImage = convertMatToBufferedImage(resizeImage);
				MemoryCleaner.clean(resizeImage);
				try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream()) {
					ImageIO.write(bufferedImage, "png", byteArrayOut);
					imageBytes = byteArrayOut.toByteArray();
				}
			} else {
				if (imageFile.getName().endsWith("tif") || imageFile.getName().endsWith("tiff")) {
					// load Mat
					final Mat imageMat = Imgcodecs.imread(imageFile.getAbsolutePath());

					// Convert to png
					BufferedImage bufferedImage = convertMatToBufferedImage(imageMat);
					MemoryCleaner.clean(imageMat);
					try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream()) {
						ImageIO.write(bufferedImage, "png", byteArrayOut);
						imageBytes = byteArrayOut.toByteArray();
					}
				} else
					imageBytes = Files.readAllBytes(imageFile.toPath());
			}

			// Create header to display the image
			HttpHeaders headers = new HttpHeaders();

			headers.setLastModified(imageFile.lastModified());
			headers.setCacheControl("no-store, no-cache");
			headers.setContentType(MediaType.IMAGE_PNG);
			headers.setContentLength(imageBytes.length);

			return new ResponseEntity<byte[]>(imageBytes, headers, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Upload a segmentation to load back into the gui.
	 * 
	 */
	@RequestMapping(value = "file/upload/annotations", method = RequestMethod.POST)
	public @ResponseBody PageAnnotations uploadSegmentation(@RequestParam("file") MultipartFile file,
			@RequestParam("pageNr") int pageNr, @RequestParam("bookID") int bookID) {
		if (!file.isEmpty()) {
			try (ByteArrayInputStream stream = new ByteArrayInputStream(file.getBytes())){
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

				return PageXMLReader.getPageAnnotations(dBuilder.parse(stream));
			} catch (SAXException | IOException | ParserConfigurationException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Export a segmentation per PAGE xml to download and or adding it to the database.
	 */
	@RequestMapping(value = "file/export/annotations", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestBody ExportRequest request) {
		try {
			final Document pageXML = PageXMLWriter.getPageXML(request.getSegmentation(), request.getVersion());

			final String xmlName =  request.getSegmentation().getName() + ".xml";

			switch (config.getSetting("localsave")) {
			case "bookpath":
				FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
					config.getListSetting("imagefilter"));

				String bookdir = fileManager.getLocalBooksPath() + File.separator
									+ database.getBookName(request.getBookid());
				PageXMLWriter.saveDocument(pageXML, xmlName, bookdir);
				break;
			case "savedir":
				String savedir = config.getSetting("savedir");
				if (savedir != null && !savedir.equals("")) {
					PageXMLWriter.saveDocument(pageXML, xmlName, savedir);
				} else {
					System.err.println("Warning: Save dir is not set. File could not been saved.");
				}
				break;
			case "none":
			case "default":
			}
			return convertDocumentToByte(pageXML, request.getSegmentation().getName());
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Download the current segmentation settings as response.
	 */
	@RequestMapping(value = "file/download/segmentsettings", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> downloadSettings(@RequestBody SegmentationSettings settings) {
		try {
			return convertDocumentToByte(LarexFacade.getSettingsXML(settings), "settings.xml");
		} catch (Exception e) {
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Upload a segmentation settings file and return a BookSettings json as result.
	 * 
	 * @param file
	 * @param bookID
	 * @return
	 */
	@RequestMapping(value = "file/upload/segmentsettings", method = RequestMethod.POST)
	public @ResponseBody SegmentationSettings uploadSettings(@RequestParam("file") MultipartFile file,
			@RequestParam("bookID") int bookID) {
		SegmentationSettings settings = null;
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				settings = LarexFacade.readSettings(bytes, bookID, fileManager, database);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return settings;
	}

	private BufferedImage convertMatToBufferedImage(final Mat imageMat) throws IOException {
		final MatOfByte imageBuffer = new MatOfByte();
		
		Imgcodecs.imencode(".png", imageMat, imageBuffer);
		final byte[] imagebytes = imageBuffer.toArray();
		
		imageBuffer.release();

		return ImageIO.read(new ByteArrayInputStream(imagebytes));
	}

	private ResponseEntity<byte[]> convertDocumentToByte(Document document, String filename) {
		// convert document to bytes
		byte[] documentbytes = null;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();) {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			transformer.transform(new DOMSource(document), new StreamResult(out));
			documentbytes = out.toByteArray();
		} catch (IOException | TransformerException e) {
			e.printStackTrace();
		}

		// create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/xml"));
		headers.setContentDispositionFormData(filename, filename + ".xml");
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		return new ResponseEntity<byte[]>(documentbytes, headers, HttpStatus.OK);
	}
}