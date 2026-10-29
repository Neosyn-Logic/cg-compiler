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
module SynchronizerFF # ( parameter stages = 2 ) ( input reset_n , input din_clock , input dout_clock , input din , output dout ) ;
reg [ stages - 1 : 0 ] w1 ;
assign dout = w1 [ stages - 1 ] ;
integer w2 ;
always @ ( negedge reset_n or posedge dout_clock ) if ( ~ reset_n ) begin
w1 <= 0 ;
end
else begin
for ( w2 = stages - 1 ;
w2 > 0 ;
w2 = w2 - 1 ) begin
w1 [ w2 ] <= w1 [ w2 - 1 ] ;
end
w1 [ 0 ] <= din ;
end
endmodule
