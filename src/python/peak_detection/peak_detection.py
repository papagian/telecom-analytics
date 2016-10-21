#
# Copyright 2015-2016 WIND,FORTH
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
__author__ = 'paul'
from pyspark import SparkContext
import numpy as np
import re
import sys

"""
Peak detection Module

Given a hourly presence dataset (usually regarding a month of activity), and a typical weekly presence dataset, it computes
the relative presences for each hour of the month, in order to identify eventual peaks of presences.

Usage: peak_detection.py  <spatial_division> <region> <timeframe>

--region,timeframe: names of the file stored into the hdfs. E.g. Roma 11-2015

example: pyspark peak_detection.py roma 06-215

It loads the hourly presences in /peaks/weekly_presence-<region>-<timeframe> and stores
results into standard csv file: rome_peaks<region>-<timeframe>-<spatial_division>.csv

"""

if __name__ == '__main__':
    hourly_dataset = sys.argv[1]
    weekly_dataset = sys.argv[2]

    sc = SparkContext()

    mean_presence = sc.pickleFile(weekly_dataset).collectAsMap()

    hourly_calls = sc.pickleFile(hourly_dataset)

    pattern = r'/peaks/hourly_(?P<region>\w+)_(?P<start_date>\w+-\w+-\w+)_(?P<wnd_date>\w+-\w+-\w+)'
    m = re.search(pattern, hourly_dataset)
    region, start_date, end_date = m.groups()
    peaks = open('peaks_%s_%s_%s.csv' % (region, start_date, end_date), 'w')
    # format: area, hour, date-> percentage
    for ((area, dow, hour, start_date), count) in hourly_calls.collect():
        print >>peaks, "%s,%s,%s,%s,%s" % (area, hour, dow, start_date,
                                           count / np.mean(list(mean_presence[(area, dow, hour)])))
