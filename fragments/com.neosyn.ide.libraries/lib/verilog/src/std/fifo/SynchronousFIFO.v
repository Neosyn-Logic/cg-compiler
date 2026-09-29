/*
 * Copyright (c) 2012-2021, Neosyn
 * All rights reserved.
 * 
 * REDISTRIBUTION of this file in source and binary forms, with or without
 * modification, is NOT permitted in any way.
 *
 * The use of this file in source and binary forms, with or without
 * modification, is permitted if you have a valid commercial license of
 * Neosyn IDE.
 * If you do NOT have a valid license of Neosyn IDE: you are NOT allowed
 * to use this file.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE
 */
module SynchronousFIFO # ( parameter size = 0 , width = 0 , depth = 0 ) ( input clock , input reset_n , input [ width - 1 : 0 ] din , input din_valid , output din_ready , output [ width - 1 : 0 ] dout , output reg dout_valid , input dout_ready ) ;
reg [ depth - 1 : 0 ] w1 , w2 ;
PseudoDualPortRAM # ( . size ( size ) , . width ( width ) , . depth ( depth ) ) w3 ( . rd_clock ( clock ) , . wr_clock ( clock ) , . rd_address ( w1 ) , . wr_address ( w2 ) , . data ( din ) , . data_valid ( din_valid ) , . q ( dout ) ) ;
wire w4 ;
assign w4 = w2 == w1 ;
wire w5 , w6 ;
generate if ( depth >= 2 ) begin
: w7 assign w5 = ( w2 + { { ( depth - 2 ) { 1'b0 } } , 2'd2 } ) == w1 ;
assign w6 = ( w2 + { { ( depth - 1 ) { 1'b0 } } , 1'd1 } ) == w1 ;
end
else begin
: w8 assign w6 = ( w2 + 1'b1 ) == w1 ;
assign w5 = w6 ;
end
endgenerate
assign din_ready = ! w5 && ! w6 ;
generate if ( depth >= 2 ) begin
: w9 always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
w1 <= { depth { 1'b0 } } ;
w2 <= { depth { 1'b0 } } ;
dout_valid <= 1'b0 ;
end
else begin
dout_valid <= 1'b0 ;
w2 <= w2 + { { ( depth - 1 ) { 1'b0 } } , din_valid } ;
if ( dout_ready && ! w4 ) begin
w1 <= w1 + 1'b1 ;
dout_valid <= 1'b1 ;
end
end
end
end
else begin
: w10 always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
w1 <= 1'b0 ;
w2 <= 1'b0 ;
dout_valid <= 1'b0 ;
end
else begin
dout_valid <= 1'b0 ;
w2 <= w2 + din_valid ;
if ( dout_ready && ! w4 ) begin
w1 <= w1 + 1'b1 ;
dout_valid <= 1'b1 ;
end
end
end
end
endgenerate
endmodule
