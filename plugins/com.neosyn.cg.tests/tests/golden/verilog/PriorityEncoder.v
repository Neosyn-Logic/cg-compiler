/**
 * Title      : Generated from com.neosyn.test.golden.PriorityEncoder by Neosyn IDE
 * File       : com.neosyn.test.golden.PriorityEncoder.v
 * Standard   : Verilog-2001
 */

/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * Priority encoder - finds highest set bit.
 */
module PriorityEncoder(
  input [7 : 0] bits,
  output reg [2 : 0] index,
  output reg  valid
);


  /**
   * State variables
   */
  
  
  
  
  /**
   * Combinational process
   */
  always @(bits) begin
    index = 3'b0;
    valid = 1'b0;
    begin : FSM_PriorityEncoder_a // line 89
      reg [7 : 0] b;
      reg [7 : 0] b_and_64;
      reg [7 : 0] b_and_32;
      reg [7 : 0] b_and_16;
      reg [7 : 0] b_and_8;
      reg [7 : 0] b_and_4;
      reg [7 : 0] b_and_2;
      b = 0;
      b_and_64 = 0;
      b_and_32 = 0;
      b_and_16 = 0;
      b_and_8 = 0;
      b_and_4 = 0;
      b_and_2 = 0;
    
      b = bits;
      if (((b & 8'h80) != 8'h0)) begin
        index = 3'h7;
        valid = 1'b1;
      end else begin
        b_and_64 = (b[6 : 0] & 7'h40);
        if ((b_and_64[6 : 0] != 7'h0)) begin
          index = 3'h6;
          valid = 1'b1;
        end else begin
          b_and_32 = (b[5 : 0] & 6'h20);
          if ((b_and_32[5 : 0] != 6'h0)) begin
            index = 3'h5;
            valid = 1'b1;
          end else begin
            b_and_16 = (b[4 : 0] & 5'h10);
            if ((b_and_16[4 : 0] != 5'h0)) begin
              index = 3'h4;
              valid = 1'b1;
            end else begin
              b_and_8 = (b[3 : 0] & 4'h8);
              if ((b_and_8[3 : 0] != 4'h0)) begin
                index = 3'h3;
                valid = 1'b1;
              end else begin
                b_and_4 = (b[2 : 0] & 3'h4);
                if ((b_and_4[2 : 0] != 3'h0)) begin
                  index = 3'h2;
                  valid = 1'b1;
                end else begin
                  b_and_2 = (b[1 : 0] & 2'h2);
                  if ((b_and_2[1 : 0] != 2'h0)) begin
                    index = 3'h1;
                    valid = 1'b1;
                  end else if (b[0]) begin
                    index = 3'h0;
                    valid = 1'b1;
                  end else begin
                    index = 3'h0;
                    valid = 1'b0;
                  end
                end
              end
            end
          end
        end
      end
    end
  end
  

endmodule //PriorityEncoder
