package de.uniwue.web.controller;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Communication Controller to handle error pages
 *
 */
@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

	@RequestMapping(value = "/error")
	public String handleError(HttpServletRequest request, Model model) throws IOException {
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		
		if (status != null) {
			int statusCode = Integer.parseInt(status.toString());
			
			switch (statusCode) {
				case 400:
					return error400(model);
				case 403:
					return error403(model);
				case 404:
					return error404(model);
				case 500:
					return error500(model);
				default:
					model.addAttribute("message", "An unexpected error occurred.");
					model.addAttribute("code", String.valueOf(statusCode));
					return "error";
			}
		}
		
		model.addAttribute("message", "An unexpected error occurred.");
		model.addAttribute("code", "500");
		return "error";
	}

	@RequestMapping(value = "/error/400")
	public String error400(Model model) {
		model.addAttribute("message", "We can't understand the request.");
		model.addAttribute("code", "400");
		return "error";
	}

	@RequestMapping(value = "/error/403")
	public String error403(Model model) {
		model.addAttribute("message", "We are sorry but you are not authorized to use the requested page.");
		model.addAttribute("code", "403");
		return "error";
	}

	@RequestMapping(value = "/error/404")
	public String error404(Model model) {
		model.addAttribute("message", "We can't seem to find the page you're looking for.");
		model.addAttribute("code", "404");
		return "error";
	}

	@RequestMapping(value = "/error/500")
	public String error500(Model model) throws IOException {
		model.addAttribute("message", "It seems like the server has mixed something up. Please check whether the book directory is accessible to LAREX and whether the directory follows the necessary structure.");
		model.addAttribute("code", "500");
		return "error";
	}
}
