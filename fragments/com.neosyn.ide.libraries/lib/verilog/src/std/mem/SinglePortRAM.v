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
 * Title   : Single-port inferred RAM
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 */
module SinglePortRAM
  #(parameter size = 0, width = 0, depth = 0, writeShiftMode = 0, addOutputRegister = 0)
  (
    input clock,
    input [depth - 1 : 0] address,
    input [width - 1 : 0] data, input data_valid,
    output [width - 1 : 0] q
  );

  /*
   * RAM contents
   */
  reg [width - 1 : 0] ram [0 : size - 1];
  reg [width - 1 : 0] dout;

  generate
    if (addOutputRegister) begin
      reg [width - 1 : 0] outputRegister;

      always @(posedge clock) begin
        outputRegister <= dout;
      end

      assign q = outputRegister;
    end else begin
      assign q = dout;
    end
  endgenerate

  // read and write data process
  always @(posedge clock) begin
    if (!data_valid || writeShiftMode) begin
      dout <= ram[address];
    end

    if (data_valid) begin
      if (!writeShiftMode) begin
        dout <= data;
      end
      ram[address] <= data;
    end
  end

endmodule
