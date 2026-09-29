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
module AsynchronousFIFO # ( parameter size = 0 , width = 0 , depth = 0 ) ( input din_clock , input dout_clock , input reset_n , input [ width - 1 : 0 ] din , input din_valid , output din_ready , output reg [ width - 1 : 0 ] dout , output reg dout_valid , input dout_ready ) ;
wire [ depth - 1 : 0 ] w1 ;
wire [ depth - 1 : 0 ] w2 ;
wire w3 , w4 ;
reg w5 ;
wire [ width - 1 : 0 ] w6 ;
DualPortRAM # ( . size ( size ) , . width ( width ) , . depth ( depth ) ) w7 ( . clock_a ( din_clock ) , . clock_b ( dout_clock ) , . address_a ( w2 ) , . data_a ( din ) , . data_a_valid ( din_valid ) , . q_a ( ) , . address_b ( w1 ) , . data_b ( ) , . data_b_valid ( ) , . q_b ( w6 ) ) ;
FIFO_Write_Controller # ( . depth ( depth ) ) w8 ( . reset_n ( reset_n ) , . wr_clock ( din_clock ) , . enable ( din_valid ) , . gray_value ( w2 ) ) ;
FIFO_Read_Controller # ( . depth ( depth ) ) w9 ( . reset_n ( reset_n ) , . rd_clock ( dout_clock ) , . enable ( w4 ) , . gray_value ( w1 ) ) ;
assign w4 = w3 & dout_ready ;
FIFO_Flag_Controller # ( . depth ( depth ) ) w10 ( . reset_n ( reset_n ) , . din_clock ( din_clock ) , . dout_clock ( dout_clock ) , . wr_address ( w2 ) , . rd_address ( w1 ) , . din_rdy ( din_ready ) , . dout_rdy ( w3 ) ) ;
always @ ( negedge reset_n or posedge dout_clock ) if ( ~ reset_n ) begin
dout <= { width { 1'b0 } } ;
dout_valid <= 1'b0 ;
end
else begin
dout_valid <= w5 ;
if ( w5 ) begin
dout <= w6 ;
end
end
always @ ( negedge reset_n or posedge dout_clock ) if ( ~ reset_n ) begin
w5 <= 1'b0 ;
end
else begin
w5 <= w4 ;
end
endmodule
