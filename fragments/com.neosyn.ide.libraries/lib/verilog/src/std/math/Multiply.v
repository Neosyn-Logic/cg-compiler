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
module Multiply # ( parameter width = 32 , parameter pwidth = 64 ) ( input clock , input reset_n , input [ width - 1 : 0 ] a , input a_valid , output a_ready , input [ width - 1 : 0 ] b , input b_valid , output b_ready , output reg [ pwidth - 1 : 0 ] p , output reg p_valid , input p_ready ) ;
localparam w1 = 1'b0 , w2 = 1'b1 ;
reg w3 ;
reg [ width - 1 : 0 ] w4 , w5 ;
reg w6 , w7 ;
assign a_ready = ( w3 == w1 ) & ~ w6 ;
assign b_ready = ( w3 == w1 ) & ~ w7 ;
wire signed [ pwidth - 1 : 0 ] w8 = $signed ( w4 ) * $signed ( w5 ) ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
p <= { pwidth { 1'b0 } } ;
p_valid <= 1'b0 ;
w6 <= 1'b0 ;
w7 <= 1'b0 ;
w3 <= w1 ;
end
else begin
case ( w3 ) w1 : begin
if ( a_valid & a_ready ) begin
w4 <= a ;
w6 <= 1'b1 ;
end
if ( b_valid & b_ready ) begin
w5 <= b ;
w7 <= 1'b1 ;
end
if ( w6 & w7 ) begin
w6 <= 1'b0 ;
w7 <= 1'b0 ;
p <= w8 ;
p_valid <= 1'b1 ;
w3 <= w2 ;
end
end
w2 : if ( p_ready ) begin
p_valid <= 1'b0 ;
w3 <= w1 ;
end
default : w3 <= w1 ;
endcase
end
end
endmodule
