/*
 * Copyright (c) 2024-2026 Neosyn (neosyn.io). All rights reserved.
 *
 * Proprietary software of Neosyn. It is licensed, not sold. Use is permitted
 * only under a valid commercial license of the Neosyn toolchain. THIS SOFTWARE
 * IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND.
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
