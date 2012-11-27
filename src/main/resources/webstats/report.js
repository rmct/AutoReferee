$(document).ready(function(){
  $(".player-toggle").attr('checked', true).change(function(){
    $('tr.transcript-event.type-player-event').hide();
    $(".player-toggle").each(function(i, e){
      if ($(e).is(':checked'))
        $('tr.transcript-event.type-player-event.player-' + $(e).data('player')).show();
    });
  });

  $("#event-filter").change(function(e){
	$(".transcript-event").toggle(this.value == "");
    $(".transcript-event.type-" + this.value).show();
  });
});

var mapImage = new Image;
mapImage.src = map.image;

var tip = $("canvas#maptip");
var ctx = tip.get(0).getContext('2d');
var tip_hide_task;

ctx.imageSmoothingEnabled = false;
ctx.webkitImageSmoothingEnabled = false;
ctx.mozImageSmoothingEnabled = false;

function drawFace(ctx, player)
{
	var face = new Image;
	face.src = 'http://minotar.net/avatar/' + player + '/16.png';
	ctx.drawImage(face, -8, -8);
}

($(".transcript-event[data-location]")
	.mouseenter(function(mouse){
		var coords = $(this).data('location').split(',');
		var x = parseInt(coords[0]) - map.x, z = parseInt(coords[2]) - map.z;

		ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0);
		ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height); ctx.restore();

		ctx.save(); ctx.translate(ctx.canvas.width/2, ctx.canvas.height/2);
		ctx.save(); ctx.scale(4, 4); ctx.drawImage(mapImage, -x, -z); ctx.restore();

		var player = $(this).data('player');
		if (player) drawFace(ctx, player); ctx.restore();

		clearInterval(tip_hide_task); tip.fadeIn(300);
	})
	.mouseleave(function(mouse){
		tip_hide_task = setTimeout(function(){ tip.stop(true).fadeOut(300); }, 100);
	})
);

$(document).mousemove(function(mouse){
	tip.css({
		left: mouse.pageX + (mouse.clientX < $(window).width() /2 ? 10 : -(tip.width()  + 10)),
		top:  mouse.pageY + (mouse.clientY < $(window).height()/2 ? 10 : -(tip.height() + 10)),
	});
});