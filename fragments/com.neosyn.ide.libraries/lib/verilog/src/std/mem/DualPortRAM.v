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
 * Title   : Dual-port inferred RAM
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 */
module DualPortRAM
  #(parameter size = 0, width = 0, depth = 0)
  (
    input clock_a, input clock_b,

    input [depth - 1 : 0] address_a,
    input [width - 1 : 0] data_a, input data_a_valid,
    output reg [width - 1 : 0] q_a,

    input [depth - 1 : 0] address_b,
    input [width - 1 : 0] data_b, input data_b_valid,
    output reg [width - 1 : 0] q_b
  );

  /*
   * RAM contents
   */
  reg [width - 1 : 0] ram [0 : size - 1];

  // process a
  always @(posedge clock_a) begin
    if (data_a_valid) begin
      ram[address_a] <= data_a;
      q_a <= data_a;
    end else
      q_a <= ram[address_a];
  end

  // process a
  always @(posedge clock_b) begin
    if (data_b_valid) begin
      ram[address_b] <= data_b;
      q_b <= data_b;
    end else
      q_b <= ram[address_b];
  end

endmodule
