package com.web.controller;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Communication Controller to handle error pages
 * 
 */
@Controller
public class ErrorController {

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
		model.addAttribute("message", "It seems like the server has mixed something up.");
		model.addAttribute("code", "500");
		return "error";
	}
}
