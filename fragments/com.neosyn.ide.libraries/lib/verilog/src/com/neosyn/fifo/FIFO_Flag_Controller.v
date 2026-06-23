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

/**
 * Title   : FIFO read controller
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 */
module FIFO_Flag_Controller
  #(parameter depth = 8)
  (
    input reset_n,
    input din_clock,
    input dout_clock,
    input [depth - 1 : 0] wr_address,
    input [depth - 1 : 0] rd_address,
    output din_rdy,
    output dout_rdy
  );

  wire aFull_i;
  wire aEmpty_i;
  
  reg [depth - 1 : 0] wr_address_1C;
  reg [depth - 1 : 0] wr_address_2C;
  reg [depth - 1 : 0] rd_address_1C;
  reg [depth - 1 : 0] rd_address_2C;

  FIFO_Flag_Async #(.depth(depth)) Flag_Async_Full
  (
    .reset_n(reset_n),
    .rd_address(rd_address_2C),
    .wr_address(wr_address),
    .aFull(aFull_i),
    .aEmpty()
  );  
   
  FIFO_Flag_Async #(.depth(depth)) Flag_Async_Empty
  (
    .reset_n(reset_n),
    .rd_address(rd_address),
    .wr_address(wr_address_2C),
    .aFull(),
    .aEmpty(aEmpty_i)
  );  
  
  assign din_rdy  = ~aFull_i;
  assign dout_rdy = ~aEmpty_i;
  
  // Sync the flags
  always @(negedge reset_n or posedge din_clock)
    if (~reset_n) begin
      rd_address_1C <= {depth{1'b0}};
      rd_address_2C <= {depth{1'b0}};
    end else begin
      rd_address_2C <= rd_address_1C;
      rd_address_1C <= rd_address;     
    end

  always @(negedge reset_n or posedge dout_clock)
    if (~reset_n) begin
      wr_address_1C <= {depth{1'b0}};
      wr_address_2C <= {depth{1'b0}};
    end else begin
      wr_address_2C <= wr_address_1C;
      wr_address_1C <= wr_address;  
    end
  
endmodule
