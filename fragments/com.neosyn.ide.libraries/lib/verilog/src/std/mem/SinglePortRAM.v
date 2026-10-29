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
module SinglePortRAM # ( parameter size = 1 , width = 1 , depth = 1 , writeShiftMode = 0 , addOutputRegister = 0 ) ( input clock , input [ depth - 1 : 0 ] address , input [ width - 1 : 0 ] data , input data_valid , output [ width - 1 : 0 ] q ) ;
reg [ width - 1 : 0 ] w1 [ 0 : size - 1 ] ;
reg [ width - 1 : 0 ] w2 ;
generate if ( addOutputRegister ) begin
reg [ width - 1 : 0 ] w3 ;
always @ ( posedge clock ) begin
w3 <= w2 ;
end
assign q = w3 ;
end
else begin
assign q = w2 ;
end
endgenerate
always @ ( posedge clock ) begin
if ( ! data_valid || writeShiftMode ) begin
w2 <= w1 [ address ] ;
end
if ( data_valid ) begin
if ( ! writeShiftMode ) begin
w2 <= data ;
end
w1 [ address ] <= data ;
end
end
endmodule
