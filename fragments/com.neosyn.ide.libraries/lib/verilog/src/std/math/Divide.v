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
module Divide # ( parameter width = 32 , parameter use_hard = 0 ) ( input clock , input reset_n , input [ width - 1 : 0 ] a , input a_valid , output a_ready , input [ width - 1 : 0 ] b , input b_valid , output b_ready , output reg [ width - 1 : 0 ] q , output reg q_valid , input q_ready ) ;
localparam w1 = 2'd0 , w2 = 2'd1 , w3 = 2'd2 ;
reg [ 1 : 0 ] w4 ;
reg [ width - 1 : 0 ] w5 , w6 ;
reg w7 , w8 ;
assign a_ready = ( w4 == w1 ) & ~ w7 ;
assign b_ready = ( w4 == w1 ) & ~ w8 ;
generate if ( use_hard != 0 ) begin
: w9 wire signed [ width - 1 : 0 ] w10 = $signed ( w5 ) / $signed ( w6 ) ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
q <= { width { 1'b0 } } ;
q_valid <= 1'b0 ;
w7 <= 1'b0 ;
w8 <= 1'b0 ;
w4 <= w1 ;
end
else begin
case ( w4 ) w1 : begin
if ( a_valid & a_ready ) begin
w5 <= a ;
w7 <= 1'b1 ;
end
if ( b_valid & b_ready ) begin
w6 <= b ;
w8 <= 1'b1 ;
end
if ( w7 & w8 ) begin
w7 <= 1'b0 ;
w8 <= 1'b0 ;
q <= ( w6 == { width { 1'b0 } } ) ? { width { 1'b0 } } : w10 ;
q_valid <= 1'b1 ;
w4 <= w3 ;
end
end
w3 : if ( q_ready ) begin
q_valid <= 1'b0 ;
w4 <= w1 ;
end
default : w4 <= w1 ;
endcase
end
end
end
else begin
: w11 localparam w12 = width + 1 ;
localparam w13 = $clog2 ( width + 2 ) ;
reg [ width : 0 ] w14 ;
reg [ width : 0 ] w15 ;
reg [ width + 1 : 0 ] w16 ;
reg [ width : 0 ] w17 ;
reg w18 ;
reg [ w13 - 1 : 0 ] w19 ;
wire [ width : 0 ] w20 = w5 [ width - 1 ] ? ( ~ { w5 [ width - 1 ] , w5 } + 1'b1 ) : { 1'b0 , w5 } ;
wire [ width : 0 ] w21 = w6 [ width - 1 ] ? ( ~ { w6 [ width - 1 ] , w6 } + 1'b1 ) : { 1'b0 , w6 } ;
wire w22 = ( w6 == { width { 1'b0 } } ) ;
wire w23 = ~ w22 & ( ( w21 & ( w21 - 1'b1 ) ) == { ( width + 1 ) { 1'b0 } } ) ;
integer w24 ;
reg [ w13 - 1 : 0 ] w25 ;
always @ ( * ) begin
w25 = { w13 { 1'b0 } } ;
for ( w24 = 0 ;
w24 <= width ;
w24 = w24 + 1 ) if ( w21 [ w24 ] ) w25 = w24 [ w13 - 1 : 0 ] ;
end
wire [ width : 0 ] w26 = w20 >> w25 ;
wire w27 = w5 [ width - 1 ] ^ w6 [ width - 1 ] ;
wire [ width + 1 : 0 ] w28 = { w16 [ width : 0 ] , w14 [ width ] } ;
wire [ width + 1 : 0 ] w29 = w28 - { 1'b0 , w15 } ;
wire w30 = ( w28 >= { 1'b0 , w15 } ) ;
wire [ width : 0 ] w31 = { w17 [ width - 1 : 0 ] , w30 } ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
q <= { width { 1'b0 } } ;
q_valid <= 1'b0 ;
w7 <= 1'b0 ;
w8 <= 1'b0 ;
w4 <= w1 ;
end
else begin
case ( w4 ) w1 : begin
if ( a_valid & a_ready ) begin
w5 <= a ;
w7 <= 1'b1 ;
end
if ( b_valid & b_ready ) begin
w6 <= b ;
w8 <= 1'b1 ;
end
if ( w7 & w8 ) begin
w7 <= 1'b0 ;
w8 <= 1'b0 ;
w18 <= w27 ;
if ( w22 ) begin
q <= { width { 1'b0 } } ;
q_valid <= 1'b1 ;
w4 <= w3 ;
end
else if ( w23 ) begin
q <= w27 ? ( ~ w26 + 1'b1 ) : w26 [ width - 1 : 0 ] ;
q_valid <= 1'b1 ;
w4 <= w3 ;
end
else begin
w14 <= w20 ;
w15 <= w21 ;
w16 <= { ( width + 2 ) { 1'b0 } } ;
w17 <= { ( width + 1 ) { 1'b0 } } ;
w19 <= w12 [ w13 - 1 : 0 ] ;
w4 <= w2 ;
end
end
end
w2 : begin
w16 <= w30 ? w29 : w28 ;
w14 <= w14 << 1 ;
w17 <= w31 ;
w19 <= w19 - 1'b1 ;
if ( w19 == { { ( w13 - 1 ) { 1'b0 } } , 1'b1 } ) begin
q <= w18 ? ( ~ w31 + 1'b1 ) : w31 [ width - 1 : 0 ] ;
q_valid <= 1'b1 ;
w4 <= w3 ;
end
end
w3 : if ( q_ready ) begin
q_valid <= 1'b0 ;
w4 <= w1 ;
end
default : w4 <= w1 ;
endcase
end
end
end
endgenerate
endmodule
