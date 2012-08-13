$(document).ready(function(){
  $("input[type=checkbox].player-toggle").attr('checked', true).change(function(){
    $('tr.transcript-event.type-player-event').hide();
    $("input[type=checkbox].player-toggle").each(function(i, e){
      if ($(e).is(':checked'))
        $('tr.transcript-event.type-player-event.player-' + $(e).data('player')).show();
    });
  });
});