#!/bin/bash

listfile=s3/maps/list.csv
>$listfile

echo "Generating $listfile"
for zip in `ls s3/maps/*.zip`
do
    file=`basename $zip`
    md5=`md5sum $zip | awk '{print $1}'`
    map=${file%%.zip}

    echo "Setting up $map..."
    echo "${map/-v/;};$file;$md5" >> $listfile
done

s3cmd -P sync s3/maps s3://autoreferee/
