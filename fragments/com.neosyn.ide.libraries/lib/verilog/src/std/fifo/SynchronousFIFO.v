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
 * Title   : Synchronous FIFO
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 */
module SynchronousFIFO
  #(parameter size = 0, width = 0, depth = 0)
  (
    input clock,
    input reset_n,
    input  [width - 1 : 0] din,  input din_valid, output din_ready,
    output [width - 1 : 0] dout, output reg dout_valid, input dout_ready
  );

  reg [depth - 1 : 0] rd_address, wr_address;

  PseudoDualPortRAM #(.size(size), .width(width), .depth(depth)) ram
  (
    .rd_clock(clock),
    .wr_clock(clock),
    .rd_address(rd_address),
    .wr_address(wr_address),
    .data(din),
    .data_valid(din_valid),
    .q(dout)
  );

  wire empty;
  assign empty = wr_address == rd_address;

  wire almost_full, full;
  generate
    if (depth >= 2) begin : gen_normal
      // Original optimized code for FIFOs with 4+ entries (depth >= 2 bits)
      assign almost_full = (wr_address + {{(depth - 2){1'b0}}, 2'd2}) == rd_address;
      assign full = (wr_address + {{(depth - 1){1'b0}}, 1'd1}) == rd_address;
    end else begin : gen_tiny
      // Simplified for 1-bit address (2 entries): almost_full == full
      assign full = (wr_address + 1'b1) == rd_address;
      assign almost_full = full;
    end
  endgenerate
  assign din_ready = !almost_full && !full;

  generate
    if (depth >= 2) begin : gen_seq_normal
      always @(negedge reset_n or posedge clock) begin
        if (~reset_n) begin
          rd_address <= {depth{1'b0}};
          wr_address <= {depth{1'b0}};
          dout_valid <= 1'b0;
        end else begin
          dout_valid <= 1'b0;
          wr_address <= wr_address + {{(depth - 1){1'b0}}, din_valid};
          if (dout_ready && !empty) begin
            rd_address <= rd_address + 1'b1;
            dout_valid <= 1'b1;
          end
        end
      end
    end else begin : gen_seq_tiny
      always @(negedge reset_n or posedge clock) begin
        if (~reset_n) begin
          rd_address <= 1'b0;
          wr_address <= 1'b0;
          dout_valid <= 1'b0;
        end else begin
          dout_valid <= 1'b0;
          wr_address <= wr_address + din_valid;
          if (dout_ready && !empty) begin
            rd_address <= rd_address + 1'b1;
            dout_valid <= 1'b1;
          end
        end
      end
    end
  endgenerate
  
endmodule
