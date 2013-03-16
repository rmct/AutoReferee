$(document).ready(function()
{
    $('[data-toggle="tooltip"]').tooltip()
    $('table.tablesorter').tablesorter({
        sortList: [[0,0]],
        headers: {
            1: { sorter: false },
            4: { sorter: false }
        }
    })
});
