/*
 * Copyright (c) 2012-2026 Neosyn (neosyn.io). All rights reserved.
 * Not licensed under the MPL-2.0.
 *
 * Free to use, including commercially, as part of hardware designs generated
 * by the C⏚ compiler, whether the open-source compiler or the commercial
 * distribution. You may not extract this file for use on its own,
 * reverse-engineer it, or redistribute it except as part of such a design or
 * of the C⏚ compiler.
 *
 * Provided "AS IS", without warranty of any kind.
 */
module PseudoDualPortRAM # ( parameter size = 1 , width = 1 , depth = 1 ) ( input rd_clock , input wr_clock , input [ depth - 1 : 0 ] rd_address , input [ depth - 1 : 0 ] wr_address , input [ width - 1 : 0 ] data , input data_valid , output reg [ width - 1 : 0 ] q ) ;
reg [ width - 1 : 0 ] w1 [ 0 : size - 1 ] ;
always @ ( posedge rd_clock ) q <= w1 [ rd_address ] ;
always @ ( posedge wr_clock ) if ( data_valid ) w1 [ wr_address ] <= data ;
endmodule
