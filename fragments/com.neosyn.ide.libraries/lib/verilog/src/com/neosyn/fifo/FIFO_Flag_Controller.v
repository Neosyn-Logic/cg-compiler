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
module FIFO_Flag_Controller # ( parameter depth = 8 ) ( input reset_n , input din_clock , input dout_clock , input [ depth - 1 : 0 ] wr_address , input [ depth - 1 : 0 ] rd_address , output din_rdy , output dout_rdy ) ;
wire w1 ;
wire w2 ;
reg [ depth - 1 : 0 ] w3 ;
reg [ depth - 1 : 0 ] w4 ;
reg [ depth - 1 : 0 ] w5 ;
reg [ depth - 1 : 0 ] w6 ;
FIFO_Flag_Async # ( . depth ( depth ) ) w7 ( . reset_n ( reset_n ) , . rd_address ( w6 ) , . wr_address ( wr_address ) , . aFull ( w1 ) , . aEmpty ( ) ) ;
FIFO_Flag_Async # ( . depth ( depth ) ) w8 ( . reset_n ( reset_n ) , . rd_address ( rd_address ) , . wr_address ( w4 ) , . aFull ( ) , . aEmpty ( w2 ) ) ;
assign din_rdy = ~ w1 ;
assign dout_rdy = ~ w2 ;
always @ ( negedge reset_n or posedge din_clock ) if ( ~ reset_n ) begin
w5 <= { depth { 1'b0 } } ;
w6 <= { depth { 1'b0 } } ;
end
else begin
w6 <= w5 ;
w5 <= rd_address ;
end
always @ ( negedge reset_n or posedge dout_clock ) if ( ~ reset_n ) begin
w3 <= { depth { 1'b0 } } ;
w4 <= { depth { 1'b0 } } ;
end
else begin
w4 <= w3 ;
w3 <= wr_address ;
end
endmodule
