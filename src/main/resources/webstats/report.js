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