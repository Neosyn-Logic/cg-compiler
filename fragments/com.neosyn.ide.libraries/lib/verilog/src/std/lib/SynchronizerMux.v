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
module SynchronizerMux # ( parameter width = 32 , parameter stages = 2 ) ( input reset_n , input din_clock , input dout_clock , input din_valid , input [ width - 1 : 0 ] din , output reg [ width - 1 : 0 ] dout ) ;
wire w1 ;
SynchronizerFF # ( . stages ( stages ) ) w2 ( . reset_n ( reset_n ) , . din_clock ( din_clock ) , . dout_clock ( dout_clock ) , . din ( din_valid ) , . dout ( w1 ) ) ;
always @ ( negedge reset_n or posedge dout_clock ) if ( ~ reset_n ) begin
dout <= 0 ;
end
else begin
if ( w1 ) dout <= din ;
end
endmodule
