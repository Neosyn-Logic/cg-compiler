/*
 * Copyright (c) 2015 - 2021, Neosyn
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
 * Title       : DemuxDDR
 * Description : Demultiplexes a Double Data Rate signal as two single data rate signals
 * Authors     : Neosyn team
 */
module DemuxDDR
  #(parameter width = 0)
  (
    input reset_n,
    input clock,
    input din_valid,
    input [width - 1 : 0] din,
    output reg rising_valid,
    output reg [width - 1 : 0] rising,
    output reg falling_valid,
    output reg [width - 1 : 0] falling
  );

  reg internal_falling_valid;
  reg [width - 1 : 0] internal_falling;

  always @(negedge reset_n or posedge clock) begin
    if (~reset_n) begin
      rising <= {width{1'b0}};
      rising_valid <= 1'b0;

      falling <= {width{1'b0}};
      falling_valid <= 1'b0;
    end else begin
      rising <= din;
      rising_valid <= din_valid;

      falling <= internal_falling;
      falling_valid <= internal_falling_valid;
    end
  end

  always @(negedge reset_n or negedge clock) begin
    if (~reset_n) begin
      internal_falling <= {width{1'b0}};
      internal_falling_valid <= 1'b0;
    end else begin
      internal_falling <= din;
      internal_falling_valid <= din_valid;
    end
  end

endmodule
