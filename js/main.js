$(document).ready(function()
{
	var backgrounds = [
		"images/background-1.png",
		"images/background-2.png",
		"images/background-3.png",
		"images/background-4.png",
		"images/background-5.png",
	];

	var backgroundsP = new Array();
	for (var i = 0; i < backgrounds.length; ++i)
	{
		var img = backgroundsP[i] = new Image;
		img.src = backgrounds[i];
	}

	var cimg = 0;
	function nextImage()
	{
		$('#background-image').animate({opacity: 0}, 500, function()
		{
			cimg = (cimg + 1) % backgroundsP.length;
			$(this).css('background-image', 'url(' + backgrounds[cimg] + ')').animate({opacity: 1}, 500);
		});
	}

	var cycleTask = setInterval(nextImage, 10000);
});
