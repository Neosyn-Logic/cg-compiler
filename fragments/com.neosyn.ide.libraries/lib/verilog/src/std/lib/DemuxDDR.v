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
module DemuxDDR # ( parameter width = 1 ) ( input reset_n , input clock , input din_valid , input [ width - 1 : 0 ] din , output reg rising_valid , output reg [ width - 1 : 0 ] rising , output reg falling_valid , output reg [ width - 1 : 0 ] falling ) ;
reg w1 ;
reg [ width - 1 : 0 ] w2 ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
rising <= { width { 1'b0 } } ;
rising_valid <= 1'b0 ;
falling <= { width { 1'b0 } } ;
falling_valid <= 1'b0 ;
end
else begin
rising <= din ;
rising_valid <= din_valid ;
falling <= w2 ;
falling_valid <= w1 ;
end
end
always @ ( negedge reset_n or negedge clock ) begin
if ( ~ reset_n ) begin
w2 <= { width { 1'b0 } } ;
w1 <= 1'b0 ;
end
else begin
w2 <= din ;
w1 <= din_valid ;
end
end
endmodule
