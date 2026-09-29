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
