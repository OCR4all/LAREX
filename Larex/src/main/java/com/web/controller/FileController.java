package com.web.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
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

import com.web.communication.ExportRequest;
import com.web.communication.SegmentationRequest;
import com.web.communication.SegmentationResult;
import com.web.communication.SegmentationStatus;
import com.web.config.FileConfiguration;
import com.web.facade.LarexFacade;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class FileController {
	@Autowired
	private LarexFacade facade;
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FileManager fileManager;
	@Autowired
	private FileConfiguration config;

	@RequestMapping(value = "/images/books/{book}/{image}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getImage(@PathVariable("book") final String book,
			@PathVariable("image") final String image, @RequestParam(value= "resize", defaultValue = "false") boolean doResize) throws IOException {
		System.out.println(doResize);
		// Find file with image name
		init();
		File directory = new File(fileManager.getBooksPath() + File.separator + book);
		File[] matchingFiles = directory.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith(image) && (name.endsWith("png") || name.endsWith("jpg") || name.endsWith("jpeg")
						|| name.endsWith("tif") || name.endsWith("tiff"));
			}
		});

		if (matchingFiles.length == 0)
			throw new IOException("File does not exist");

		// load Mat
		Mat imageMat = Highgui.imread(matchingFiles[0].getAbsolutePath());

		// resize
		if(doResize) {
			Mat resizeImage = new Mat();
			int width = 300;
			int height = (int) (imageMat.rows()*((width*1.0)/imageMat.cols()));
			Size sz = new Size(width,height);
			Imgproc.resize(imageMat, resizeImage, sz );
			imageMat.release();
			imageMat = resizeImage;
		}
		
		// Convert to png
		BufferedImage bufferedImage = convertMatToBufferedImage(imageMat);
		ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
		ImageIO.write(bufferedImage, "png", byteArrayOut);
		byte[] pngImageBytes = byteArrayOut.toByteArray();

		// Create header to display the image
		HttpHeaders headers = new HttpHeaders();
		headers.setLastModified(Calendar.getInstance().getTime().getTime());
		headers.setCacheControl("no-cache");
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(pngImageBytes.length);

		return new ResponseEntity<byte[]>(pngImageBytes, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/uploadSegmentation", method = RequestMethod.POST)
	public @ResponseBody SegmentationResult uploadSegmentation(@RequestParam("file") MultipartFile file,
			@RequestParam("pageNr") int pageNr) {
		SegmentationResult result = null;
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				BookSegmentation segmentation = facade.readPageXML(bytes, pageNr);
				result = new SegmentationResult(segmentation, SegmentationStatus.SUCCESS);
			} catch (Exception e) {
			}
		}
		return result;
	}

	@RequestMapping(value = "/prepareExport", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody String prepareExport(@RequestBody ExportRequest exportRequest) {
		facade.prepareExport(exportRequest);
		return "Export has been prepared";
	}

	@RequestMapping(value = "/exportXML")
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestParam("version") String version) {
		init();
		String localsave = config.getSetting("localsave");
		switch (localsave) {
		case "bookpath":
			facade.savePageXMLLocal(fileManager.getBooksPath()+File.separator+facade.getBook().getName(), version);
			break;
		case "savedir":
			String savedir = config.getSetting("savedir");
			if (savedir != null && !savedir.equals("")) {
				facade.savePageXMLLocal(savedir, version);
			} else {
				System.err.println("Warning: Save dir is not set. File could not been saved.:w");
			}
			break;
		case "none":
		case "default":
		}
		return facade.getPageXML(version);
	}

	@RequestMapping(value = "/saveSettings", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody String saveSettings(@RequestBody SegmentationRequest exportRequest) {
		facade.prepareSettings(exportRequest.getSettings());
		return "Export has been prepared";
	}

	@RequestMapping(value = "/downloadSettings")
	public @ResponseBody ResponseEntity<byte[]> downloadSettings() {
		return facade.getSettingsXML();
	}

	@RequestMapping(value = "/uploadSettings", method = RequestMethod.POST)
	public @ResponseBody BookSettings uploadSettings(@RequestParam("file") MultipartFile file) {
		BookSettings settings = facade.getDefaultSettings(facade.getBook());
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				settings = facade.readSettings(bytes);
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
				fileManager.setBooksPath(bookFolder);
			}
		}
	}
	
	private BufferedImage convertMatToBufferedImage(Mat imageMat) {
		BufferedImage bufferedImage = null;
		int imageHeight = imageMat.rows();
		int imageWidth = imageMat.cols();
        byte[] data = new byte[imageHeight * imageWidth * (int)imageMat.elemSize()];
        int type;
        imageMat.get(0, 0, data);

        if(imageMat.channels() == 1)
            type = BufferedImage.TYPE_BYTE_GRAY;
        else
            type = BufferedImage.TYPE_3BYTE_BGR;
        
        bufferedImage = new BufferedImage(imageWidth, imageHeight, type);

        bufferedImage.getRaster().setDataElements(0, 0, imageWidth, imageHeight, data);
        return bufferedImage;
	}
}
