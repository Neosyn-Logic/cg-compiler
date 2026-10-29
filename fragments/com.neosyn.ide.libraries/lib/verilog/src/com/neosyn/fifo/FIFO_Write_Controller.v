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
module FIFO_Write_Controller # ( parameter depth = 8 ) ( input reset_n , input wr_clock , input enable , output [ depth - 1 : 0 ] gray_value ) ;
reg [ depth - 1 : 0 ] w1 ;
always @ ( negedge reset_n or posedge wr_clock ) if ( ~ reset_n ) w1 <= { depth { 1'b0 } } ;
else w1 <= w1 + enable ;
assign gray_value [ depth - 1 : 0 ] = w1 [ depth - 1 : 0 ] ^ { 1'b0 , w1 [ depth - 1 : 1 ] } ;
endmodule
