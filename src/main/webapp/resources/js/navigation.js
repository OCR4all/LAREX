$(document).ready(function () {
	let _customXmlFolder = false;
	let _communicator = new Communicator();
	let metsMap = new Map;
	$('.modal').modal();
	let checkForOldProject = function () {
		_communicator.getOldRequestData().done((data) => {
			if(data) {
				$('#reloadLastProject').show();
				let reloading = sessionStorage.getItem("reloading");
				if (reloading && reloading == "true") {
					//sessionStorage.removeItem("reloading");
					$('#reloadLastProject').trigger('click');
				}
			} else {
				$('#reloadLastProject').hide();
			}
		});
	}
	checkForOldProject();
	$('.bookopen').click(function () {
		$('#viewerNext').addClass('disabled');
		let book_id = $(this).attr('id');
		let path =  String($(this).attr('data-path'));
		let type = String($(this).attr('data-type'));
		$('#pageSection').hide();
		if(type === "mets" || type === "mets-data") {
			$('#fileGrp-div').show();
			_communicator.getMetsData(path).done((data) => {
				$('#openBookModal').modal('open');
				if(type === "mets-data") {
					path += "/data";
				}
				//remove old filegrps
				let node = document.getElementById('file-grp')
				while(node.firstChild) {
					node.removeChild(node.lastChild);
				}
				metsMap = data;
				for( let fileGrp of Object.keys(data)) {
					let pathList = data[fileGrp];
					if(pathList.length > 0) {
						let fileGrpItem = document.createElement('li');
						fileGrpItem.setAttribute('data',fileGrp);
						fileGrpItem.setAttribute('data-mets', path);
						fileGrpItem.setAttribute('class', "selectFileGrp")
						fileGrpItem.appendChild(document.createTextNode(fileGrp));
						document.getElementById('file-grp').appendChild(fileGrpItem);
					}
				}
			});
		} else if(type === "flat") {
			$('#fileGrp-div').hide();
			_communicator.getLibraryBookPages(book_id,path,type).done((data) => {
				let pageList = [];
				for( let key of Object.keys(data)) {
					let pathList = data[key];
					let pathListWithMime = [];
					for(let pathL of pathList) {
						let mime = "image/" + splitExt(pathL);
						let pathWithMime = [];
						pathWithMime.push(pathL);
						pathWithMime.push(mime);
						pathListWithMime.push(pathWithMime);
					}
					pageList.push(pathListWithMime);
				}
				if(pageList.length > 0) {
					showPages(pageList, path);
					$('#openBookModal').modal('open');
				} else {
					$('#modal_emptyProject').modal('open');
				}
			});
		} else {
			console.log("No viable files found in directory");
		}
	});

	$("#fileGrp-div").on('click', 'li.selectFileGrp',function () {
		let fileGrp = String($(this).attr('data'));
		let path = String($(this).attr('data-mets'));
		document.getElementById("FileGrpBtn").innerHTML = fileGrp;
		let pageList = metsMap[fileGrp];
		showPages(pageList, path);
	});

	$('.library-xml-setting').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_customXmlFolder = $switchBox.prop('checked');
		if(_customXmlFolder) {
			$('#xml_folder_input').removeClass('hide');
		} else {
			$('#xml_folder_input').addClass('hide');
		}
	});

	$('#viewerNext').click(function (e) {
		let customFolder;
		if(_customXmlFolder) {
			customFolder = document.getElementById("xml_folder").value;
		}
		let pageElemList = document.getElementsByClassName('libraryPage');
		let fileMap = JSON.parse('{}');
		let mimeTypMap = JSON.parse('{}');
		let metsFilePath = pageElemList[0].getAttribute('data-mets-path');
		for(let i = 0; i<pageElemList.length;i++) {
			if(pageElemList[i].checked) {
				let fileKey = pageElemList[i].getAttribute('id');
				let pathList = pageElemList[i].getAttribute('data-page').split(':');
				let filePathList = [];
				let filePath = pathList[0];
				for(let j = 0; j<pathList.length; j++) {
					if(j % 2 == 0) {
						filePath = pathList[j];
						filePathList.push(filePath);
					} else {
						mimeTypMap[filePath] = pathList[j];
					}
				}
				fileMap[fileKey] = filePathList;
			}
		}
		$.ajax({
			url : "/Larex/directLibrary",
			type: "POST",
			data: { "fileMap" : JSON.stringify(fileMap),
					"mimeMap" : JSON.stringify(mimeTypMap),
					"metsFilePath" : JSON.stringify(metsFilePath),
					"customFlag" : _customXmlFolder,
					"customFolder" : JSON.stringify(customFolder)},
			async : false,
			target : '_self',
			success : function(data) {
				console.log("Direct request successful!")
				let win = window.open("text/html", "_self");
				win.document.write(data);
				win.document.close();
			},
		});
		checkForOldProject();
	});

	function showPages(pageList, metsPath) {
		//clear previous pageList
		let node = document.getElementById('bookImageList')
		while(node.firstChild) {
			node.removeChild(node.lastChild);
		}
		for(let pathList of pageList) {
			let pageName = splitName(getPath(pathList[0]));

			// build label string from various pageSubExtensions
			let pageLabel = splitNameWithExt(getPath(pathList[0]));
			if(pathList.length > 1) {
				for(let i = 1; i < pathList.length; i++) {
					pageLabel += " | ";
					pageLabel += splitNameWithExt(getPath(pathList[i]));
				}
			}
			let pageItem = document.createElement('li');
			let pageCheckbox = document.createElement('input');
			pageCheckbox.setAttribute('type',"checkbox");
			pageCheckbox.setAttribute('checked',"checked");
			pageCheckbox.setAttribute('id',pageName);
			let pathWithMime = [];
			for(let path of pathList) {
				pathWithMime.push(path.join(':'))
			}
			pageCheckbox.setAttribute('data-page',pathWithMime.join(':'));
			pageCheckbox.setAttribute('data-mets-path',metsPath);
			pageCheckbox.setAttribute('data-mime', getMime(pathList[0]))
			pageCheckbox.setAttribute('class',"libraryPage");
			let pageCheckboxLabel = document.createElement('label');
			pageCheckboxLabel.setAttribute('for',pageName);
			pageCheckboxLabel.appendChild(document.createTextNode(pageLabel));

			pageItem.appendChild(pageCheckbox);
			pageItem.appendChild(pageCheckboxLabel);
			document.getElementById('bookImageList').appendChild(pageItem);
		}
		$('#viewerNext').removeClass('disabled');
		$('#pageSection').show();
	}
	let splitName = function (str) {
		return splitNameWithExt(str).split('.')[0];
	}
	let splitNameWithExt = function (str) {
		return str.split('\\').pop().split('/').pop();
	}
	let splitExt = function (str) {
		return str.split('.').pop();
	}
	let getPath = function (pathWithMime) {
		if(pathWithMime.type !== "undefined") {
			return pathWithMime[0];
		}
	}
	let getMime = function (pathWithMime) {
		if(pathWithMime.type !== "undefined") {
			return pathWithMime[1];
		}
	}
	$('#reloadLastProject').click(function () {
		_communicator.getOldRequestData().done((oldRequest) => {
			if(oldRequest){

				$.ajax({
					url : "/Larex/directLibrary",
					type: "POST",
					data: { "fileMap" : oldRequest.fileMapString,
							"mimeMap" : oldRequest.mimeMapString,
							"metsFilePath" : oldRequest.metsPath,
							"customFlag" : oldRequest.customFlag,
							"customFolder" : oldRequest.customFolder},
					async : false,
					target : '_self',
					success : function(data) {
						console.log("Direct request successful!")
						let win = window.open("", "_self");
						win.document.write(data);
						win.document.close();
					},
				});
			} else {
				console.log("no last project found");
			}
		});
	});
});
