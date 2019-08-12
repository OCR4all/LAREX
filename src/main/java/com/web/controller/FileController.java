package com.web.controller;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
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
import com.web.facade.segmentation.LarexFacade;
import com.web.facade.segmentation.SegmentationSettings;
import com.web.io.FileManager;
import com.web.io.PageXMLWriter;
import com.web.model.PageAnnotations;
import com.web.model.database.FileDatabase;

import larex.data.MemoryCleaner;

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
	 * Upload a segmentation to potentially save in the database and load back into the gui.
	 * 
	 */
	@RequestMapping(value = "/uploadSegmentation", method = RequestMethod.POST)
	public @ResponseBody PageAnnotations uploadSegmentation(@RequestParam("file") MultipartFile file,
			@RequestParam("pageNr") int pageNr, @RequestParam("bookID") int bookID) {
		PageAnnotations result = null;
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
				result = LarexFacade.readPageXML(bytes, pageNr, bookID, database);
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * Export a segmentation per PAGE xml to download and or in the database.
	 */
	@RequestMapping(value = "/exportXML", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestBody ExportRequest request) {
		try {
			final Document pageXML = PageXMLWriter.getPageXML(request.getSegmentation(), request.getVersion());
			final String name = request.getSegmentation().getName();

			switch (config.getSetting("localsave")) {
			case "bookpath":
				FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
					config.getListSetting("imagefilter"));

				String bookdir = fileManager.getLocalBooksPath() + File.separator
									+ LarexFacade.getBook(request.getBookid(), database).getName();
				PageXMLWriter.saveDocument(pageXML, name, bookdir);
				break;
			case "savedir":
				String savedir = config.getSetting("savedir");
				if (savedir != null && !savedir.equals("")) {
					PageXMLWriter.saveDocument(pageXML, name, savedir);
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
	@RequestMapping(value = "/downloadSettings", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> downloadSettings(@RequestBody SegmentationRequest exportRequest) {
		try {
			return convertDocumentToByte(LarexFacade.getSettingsXML(exportRequest.getSettings(), exportRequest.getPage()), "settings.xml");
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
	@RequestMapping(value = "/uploadSettings", method = RequestMethod.POST)
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

	/**
	 * Retrieve the default virtual keyboard.
	 * 
	 * @param file
	 * @param bookID
	 * @return
	 */
	@RequestMapping(value = "/virtualkeyboard", method = RequestMethod.POST)
	public @ResponseBody List<String[]> virtualKeyboard() {
		File virtualKeyboard = new File(fileManager.getVirtualKeyboardFile());

		List<String[]> keyboard = new ArrayList<>();
		try(BufferedReader br = new BufferedReader(new FileReader(virtualKeyboard))) {
			String st; 
			while ((st = br.readLine()) != null) 
				if(st.replace("\\s+", "").length() > 0) 
					keyboard.add(st.split("\\s+"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return keyboard;
	}

	private BufferedImage convertMatToBufferedImage(final Mat imageMat) {
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