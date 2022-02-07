package de.uniwue.web.controller;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import de.uniwue.web.config.Constants;
import de.uniwue.web.model.Book;
import de.uniwue.web.model.Page;
import org.apache.commons.io.FileUtils;
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
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.w3c.dom.Document;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.web.communication.ExportRequest;
import de.uniwue.web.communication.BatchExportRequest;
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
@Scope("session")
public class FileController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FilePathManager fileManager;
	@Autowired
	private LarexConfiguration config;
	/**
	 * Progress of the batchExport process
	 */
	private int exportProgress = -1;

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
			String saveDir = config.getSetting("savedir");
			if (!bookFolder.equals("")) {
				fileManager.setLocalBooksPath(bookFolder);
			}
			if (saveDir != null && !saveDir.equals("")) {
				fileManager.setSaveDir(saveDir);
			}
		}
		this.exportProgress = 0;
	}

	/**
	 * Request an image of a book, by book name and image name.
	 * Use resize to get a downscaled preview image, with a width of 300px.
	 */
	@RequestMapping(value = "loadImage/{imageEnc}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getImage(@PathVariable("imageEnc") final String imageEnc,
			@RequestParam(value = "resize", defaultValue = "false") boolean doResize) throws IOException {
		try {

			File imageFile;
			byte[] imageBytes = null;
			String image = java.net.URLDecoder.decode(imageEnc, StandardCharsets.UTF_8.name()).replaceAll("‡","/");
			if(image.startsWith("\"")) {	image = image.substring(1); }
			if(fileManager.checkFlat()) {
				// Find file with image name
				final File directory = new File(fileManager.getLocalBooksPath() + File.separator);
				final String imageName = image.replace(".png", "");

				final File[] matchingFiles = directory.listFiles((File dir, String name) -> {
							final int extStart = name.lastIndexOf(".");
							if (extStart > 0 && name.substring(0, extStart).equals(imageName)) {
								final String extension = name.substring(extStart + 1);
								return Constants.IMG_EXTENSIONS.contains(extension);
							} else {
								return false;
							}
						}
				);
				assert matchingFiles != null;
				if (matchingFiles.length == 0)
					throw new IOException("File does not exist");
				imageFile = matchingFiles[0];
			} else {
				List<String> foundImages = new LinkedList<>();
				Map<String, List<String>> localImageMap = fileManager.getLocalImageMap();
				for(List<String> imgPathList : localImageMap.values()) {
					for(String imgPath : imgPathList) {
						File imgFile = new File(imgPath);
						if(imgFile.getAbsolutePath().contains(image)) {
							foundImages.add(imgPath);
						}
					}
				}

				imageFile = new File(foundImages.get(0));
			}

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
			e.printStackTrace();
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Upload a segmentation to load back into the gui.
	 *
	 */
	@RequestMapping(value = "file/upload/annotations", method = RequestMethod.POST, headers = "Accept=*/*")
	public @ResponseBody PageAnnotations uploadSegmentation(@RequestParam("file") CommonsMultipartFile multipart,
															@RequestParam("pageNr") int pageNr,
															@RequestParam("bookID") int bookID,
															@RequestParam("xmlName") String xmlName) throws IOException {
		multipart.getFileItem().getName();
		File file = new File(xmlName);
		FileUtils.writeByteArrayToFile(file, multipart.getBytes());
		FileUtils.writeByteArrayToFile(file, multipart.getBytes());
		PageAnnotations annotations = PageXMLReader.getPageAnnotations(file);
		file.delete();
		return annotations;
	}

	/**
	 * Export a segmentation per PAGE xml to download and or adding it to the database.
	 */
	@RequestMapping(value = "file/export/annotations", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestBody ExportRequest request) {
		try {
			String xmlPath =  fileManager.getLocalXmlMap().get(request.getSegmentation().getXmlName().split("\\.")[0]);
			Integer bookId = request.getBookid();

			final Document pageXML = PageXMLWriter.getPageXML(request.getSegmentation(), request.getVersion(), new File(xmlPath));

			saveDocument(pageXML, xmlPath, bookId);

			byte[] docBytes = convertDocumentToByte(pageXML);
			return convertByteToResponse(docBytes, request.getSegmentation().getXmlName(), "application/xml");
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Export multiple segmentation per PAGE xml to download and or adding it to the database.
	 */
	@RequestMapping(value = "file/export/batchExport", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/zip", consumes = "application/json")
	public @ResponseBody ResponseEntity<byte[]> BatchExportXML(@RequestBody BatchExportRequest request) {
		try {
			List<PageAnnotations> segmentations = request.getSegmentation();
			List<String> filenames = new ArrayList<String>();
			List<Document> docs = new ArrayList<>();
			Integer bookId = request.getBookid();
			for(int i = 0;  i < request.getPages().size(); i++) {
				String xmlName =  segmentations.get(i).getName() + ".xml";
				File xmlFile = getXMLFilePath(xmlName, bookId);

				Document pageXML = PageXMLWriter.getPageXML(segmentations.get(i), request.getVersion(), xmlFile);
				filenames.add(xmlName);
				docs.add(pageXML);
				if(fileManager.checkFlat()) {
					saveDocument(pageXML, xmlName, request.getBookid());
				} else {
					String xmlPath = fileManager.getLocalXmlMap().get(segmentations.get(i).getXmlName().split("\\.")[0]);
					saveDocument(pageXML, xmlPath, request.getBookid());
				}
			}

			if(request.getDownload()) {
				byte[] zipBytes = convertDocumentsToArchive(docs,filenames);
				return convertByteToResponse(zipBytes,"archive.zip","application/zip");
			} else {
				return new ResponseEntity<byte[]>(HttpStatus.OK);
			}
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
			byte[] xmlBytes = convertDocumentToByte(LarexFacade.getSettingsXML(settings));
			return convertByteToResponse(xmlBytes,"settings.xml", "application/xml");
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
				config.getListSetting("imagefilter"),fileManager.checkFlat());
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

	/**
	 * Converts Document to byteArray
	 * @param document org.w3c.dom.Document
	 * @return byteArray
	 */
	private byte[] convertDocumentToByte(Document document) {
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

		return documentbytes;
	}

	/**
	 * Archives list of Documents to zip and the converts archive to byteArray
	 * @param documents List of org.w3c.dom.Document
	 * @param filenames List of IDs corresponding to each document
	 * @return
	 */
	private byte[] convertDocumentsToArchive(List<Document> documents, List<String> filenames) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ZipOutputStream zos = new ZipOutputStream(baos);
			for(int i = 0; i < documents.size(); i++) {
				//byte[] doc = convertDocumentToByte(documents.get(i));
				ByteArrayInputStream bais = new ByteArrayInputStream(convertDocumentToByte(documents.get(i)));
				ZipEntry entry = new ZipEntry(filenames.get(i));
				//entry.setSize(doc.length);
				zos.putNextEntry(entry);
				byte[] buffer = new byte[1024];
				int len = 0;
				while ((len = bais.read(buffer)) > 0) {
					zos.write(buffer, 0,len);
				}
				zos.closeEntry();
			}
			zos.close();
			baos.close();

			return baos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
		}
		//return baos.toByteArray();
	}

	/**
	 * Creates a ResponseEntity from Bytes
	 * @param bytes byteArray
	 * @param filename	name of file in byteArray
	 * @param mediaType mediaType of Response
	 * @return new HTTP ResponseEntity
	 */
	private ResponseEntity<byte[]> convertByteToResponse(byte[] bytes, String filename, String mediaType) {
		// create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(mediaType));

		headers.setContentDispositionFormData(filename, filename);
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<byte[]>(bytes, headers, HttpStatus.OK);
	}

	private File getXMLFilePath(String xmlName, Integer bookid){
		FileDatabase database;
		switch (config.getSetting("localsave")) {
			case "bookpath":
				database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
						config.getListSetting("imagefilter"), fileManager.checkFlat());
				if( fileManager.checkFlat()) {
					String bookdir = fileManager.getLocalBooksPath() + File.separator
							+ database.getBookName(bookid);
					if(!bookdir.endsWith(File.separator)) { bookdir += File.separator; }
					return new File(bookdir + xmlName);
				} else {
					return new File(xmlName);
				}
			case "savedir":
				if( fileManager.checkFlat()) {
					String savedir = config.getSetting("savedir");
					if (savedir != null && !savedir.equals("")) {
						if(!savedir.endsWith(File.separator)) { savedir += File.separator; }
						return new File(savedir + xmlName);
					} else {
						System.err.println("Warning: Save dir is not set. File could not been saved.");
					}
				} else {
					return new File(fileManager.getLocalXmlMap().get(xmlName.split("\\.")[0]));
				}
			case "none":
			case "default":
				return null;
		}
		return null;
	}

	private void saveDocument(Document pageXML, String xmlName, Integer bookid) {
		File xmlFile = getXMLFilePath(xmlName, bookid);

		if(xmlFile != null){
			PageXMLWriter.saveDocument(pageXML, xmlFile, true);
		}
	}

	/**
	 * Response to the request to return the progress status of the adjust files service
	 *
	 * @param session Session of the user
	 * @return Current progress (range: 0 - 100)
	 */
	@RequestMapping(value = "file/export/batchExportProgress" , method = RequestMethod.GET)
	public @ResponseBody int progress(HttpSession session, HttpServletResponse response) { return this.exportProgress; }
}
