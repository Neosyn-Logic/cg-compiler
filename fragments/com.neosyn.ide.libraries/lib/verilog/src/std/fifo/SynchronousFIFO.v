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
module SynchronousFIFO # ( parameter size = 1 , width = 1 , depth = 1 ) ( input clock , input reset_n , input [ width - 1 : 0 ] din , input din_valid , output din_ready , output reg [ width - 1 : 0 ] dout , output reg dout_valid , input dout_ready , output almost_full ) ;
reg [ depth - 1 : 0 ] w1 , w2 ;
reg [ depth : 0 ] w3 ;
reg w4 ;
wire [ width - 1 : 0 ] w5 ;
wire w6 = dout_valid && dout_ready ;
wire w7 = ! dout_valid || w6 ;
wire w8 = w7 && w4 ;
wire w9 = din_valid && din_ready ;
wire w10 = w7 && w9 && w3 == 0 ;
wire w11 = w9 && ! w10 ;
assign din_ready = ( w3 + dout_valid ) < size ;
assign almost_full = ( w3 + dout_valid + 3 ) > size ;
wire [ depth - 1 : 0 ] w12 = ( w1 == size - 1 ) ? { depth { 1'b0 } } : w1 + 1'b1 ;
wire [ depth - 1 : 0 ] w13 = ( w2 == size - 1 ) ? { depth { 1'b0 } } : w2 + 1'b1 ;
PseudoDualPortRAM # ( . size ( size ) , . width ( width ) , . depth ( depth ) ) w14 ( . rd_clock ( clock ) , . wr_clock ( clock ) , . rd_address ( w8 ? w12 : w1 ) , . wr_address ( w2 ) , . data ( din ) , . data_valid ( w11 ) , . q ( w5 ) ) ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
w1 <= { depth { 1'b0 } } ;
w2 <= { depth { 1'b0 } } ;
w3 <= { ( depth + 1 ) { 1'b0 } } ;
w4 <= 1'b0 ;
dout <= { width { 1'b0 } } ;
dout_valid <= 1'b0 ;
end
else begin
if ( w8 ) begin
dout <= w5 ;
dout_valid <= 1'b1 ;
w1 <= w12 ;
end
else if ( w10 ) begin
dout <= din ;
dout_valid <= 1'b1 ;
end
else if ( w6 ) begin
dout_valid <= 1'b0 ;
end
if ( w11 ) begin
w2 <= w13 ;
end
w3 <= w3 + w11 - w8 ;
w4 <= ( w3 - w8 ) != 0 ;
end
end
endmodule
