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
module DualPortRAM # ( parameter size = 1 , width = 1 , depth = 1 ) ( input clock_a , input clock_b , input [ depth - 1 : 0 ] address_a , input [ width - 1 : 0 ] data_a , input data_a_valid , output reg [ width - 1 : 0 ] q_a , input [ depth - 1 : 0 ] address_b , input [ width - 1 : 0 ] data_b , input data_b_valid , output reg [ width - 1 : 0 ] q_b ) ;
reg [ width - 1 : 0 ] w1 [ 0 : size - 1 ] ;
always @ ( posedge clock_a ) begin
if ( data_a_valid ) begin
w1 [ address_a ] <= data_a ;
q_a <= data_a ;
end
else q_a <= w1 [ address_a ] ;
end
always @ ( posedge clock_b ) begin
if ( data_b_valid ) begin
w1 [ address_b ] <= data_b ;
q_b <= data_b ;
end
else q_b <= w1 [ address_b ] ;
end
endmodule
