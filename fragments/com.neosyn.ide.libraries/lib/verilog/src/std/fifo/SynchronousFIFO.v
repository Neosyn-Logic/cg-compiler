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
module SynchronousFIFO # ( parameter size = 1 , width = 1 , depth = 1 ) ( input clock , input reset_n , input [ width - 1 : 0 ] din , input din_valid , output din_ready , output reg [ width - 1 : 0 ] dout , output reg dout_valid , input dout_ready , output almost_full ) ;
reg [ depth - 1 : 0 ] w1 , w2 ;
reg [ depth : 0 ] w3 ;
reg w4 ;
wire [ width - 1 : 0 ] w5 ;
wire w6 = dout_valid && dout_ready ;
wire w7 = ! dout_valid || w6 ;
wire w8 = w7 && w4 ;
wire w9 = din_valid && din_ready ;
wire w10 = w7 && w9 && w3 == 0 ;
wire w11 = w9 && ! w10 ;
assign din_ready = ( w3 + dout_valid ) < size ;
assign almost_full = ( w3 + dout_valid + 3 ) > size ;
wire [ depth - 1 : 0 ] w12 = ( w1 == size - 1 ) ? { depth { 1'b0 } } : w1 + 1'b1 ;
wire [ depth - 1 : 0 ] w13 = ( w2 == size - 1 ) ? { depth { 1'b0 } } : w2 + 1'b1 ;
PseudoDualPortRAM # ( . size ( size ) , . width ( width ) , . depth ( depth ) ) w14 ( . rd_clock ( clock ) , . wr_clock ( clock ) , . rd_address ( w8 ? w12 : w1 ) , . wr_address ( w2 ) , . data ( din ) , . data_valid ( w11 ) , . q ( w5 ) ) ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
w1 <= { depth { 1'b0 } } ;
w2 <= { depth { 1'b0 } } ;
w3 <= { ( depth + 1 ) { 1'b0 } } ;
w4 <= 1'b0 ;
dout <= { width { 1'b0 } } ;
dout_valid <= 1'b0 ;
end
else begin
if ( w8 ) begin
dout <= w5 ;
dout_valid <= 1'b1 ;
w1 <= w12 ;
end
else if ( w10 ) begin
dout <= din ;
dout_valid <= 1'b1 ;
end
else if ( w6 ) begin
dout_valid <= 1'b0 ;
end
if ( w11 ) begin
w2 <= w13 ;
end
w3 <= w3 + w11 - w8 ;
w4 <= ( w3 - w8 ) != 0 ;
end
end
endmodule
