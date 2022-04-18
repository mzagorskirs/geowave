#
# Copyright (c) 2013-2022 Contributors to the Eclipse Foundation

#
# See the NOTICE file distributed with this work for additional information regarding copyright
# ownership. All rights reserved. This program and the accompanying materials are made available
# under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
# available at http://www.apache.org/licenses/LICENSE-2.0.txt
# ===============================================================================================
from pygw.config import geowave_pkg
from ..statistic_binning_strategy import StatisticBinningStrategy


class DataTypeBinningStrategy(StatisticBinningStrategy):
    """
    Statistic binning strategy that bins statistic values by data type name. This is generally only used for index
    statistics because data type and field statistics would all go under the same bin.
    """

    def __init__(self, java_ref=None):
        if java_ref is None:
            java_ref = geowave_pkg.core.store.statistics.binning.DataTypeBinningStrategy()
        super().__init__(java_ref)
