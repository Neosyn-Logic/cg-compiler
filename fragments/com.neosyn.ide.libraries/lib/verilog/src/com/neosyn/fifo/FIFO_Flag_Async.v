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
module FIFO_Flag_Async # ( parameter depth = 8 ) ( input reset_n , input [ depth - 1 : 0 ] rd_address , input [ depth - 1 : 0 ] wr_address , output aFull , output aEmpty ) ;
wire w1 ;
wire w2 ;
reg w3 ;
assign w1 = ~ ( ( wr_address [ depth - 1 ] ^ rd_address [ depth - 2 ] ) & ( ~ ( wr_address [ depth - 2 ] ^ rd_address [ depth - 1 ] ) ) ) ;
assign w2 = ~ ( ( wr_address [ depth - 2 ] ^ rd_address [ depth - 1 ] ) & ( ~ ( wr_address [ depth - 1 ] ^ rd_address [ depth - 2 ] ) ) | ( ~ reset_n ) ) ;
assign aFull = w3 & ( wr_address == rd_address ) ;
assign aEmpty = ~ w3 & ( wr_address == rd_address ) ;
always @ ( w1 , w2 ) if ( ~ w2 ) w3 <= 1'b0 ;
else if ( ~ w1 ) w3 <= 1'b1 ;
endmodule
