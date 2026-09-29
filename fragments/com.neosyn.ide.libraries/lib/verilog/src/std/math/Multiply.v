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
module Multiply # ( parameter width = 32 , parameter pwidth = 64 ) ( input clock , input reset_n , input [ width - 1 : 0 ] a , input a_valid , output a_ready , input [ width - 1 : 0 ] b , input b_valid , output b_ready , output reg [ pwidth - 1 : 0 ] p , output reg p_valid , input p_ready ) ;
localparam w1 = 1'b0 , w2 = 1'b1 ;
reg w3 ;
reg [ width - 1 : 0 ] w4 , w5 ;
reg w6 , w7 ;
assign a_ready = ( w3 == w1 ) & ~ w6 ;
assign b_ready = ( w3 == w1 ) & ~ w7 ;
wire signed [ pwidth - 1 : 0 ] w8 = $signed ( w4 ) * $signed ( w5 ) ;
always @ ( negedge reset_n or posedge clock ) begin
if ( ~ reset_n ) begin
p <= { pwidth { 1'b0 } } ;
p_valid <= 1'b0 ;
w6 <= 1'b0 ;
w7 <= 1'b0 ;
w3 <= w1 ;
end
else begin
case ( w3 ) w1 : begin
if ( a_valid & a_ready ) begin
w4 <= a ;
w6 <= 1'b1 ;
end
if ( b_valid & b_ready ) begin
w5 <= b ;
w7 <= 1'b1 ;
end
if ( w6 & w7 ) begin
w6 <= 1'b0 ;
w7 <= 1'b0 ;
p <= w8 ;
p_valid <= 1'b1 ;
w3 <= w2 ;
end
end
w2 : if ( p_ready ) begin
p_valid <= 1'b0 ;
w3 <= w1 ;
end
default : w3 <= w1 ;
endcase
end
end
endmodule
