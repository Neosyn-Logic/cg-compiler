/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * Decoder - 3-to-8 decoder using lookup.
 */
module Decoder3to8(input [2 : 0] sel, input  enable, output reg [7 : 0] y);


  /**
   * State variables
   */
  
  
  
  
  /**
   * Combinational process
   */
  always @(sel or enable) begin
    y = 8'b0;
    begin : FSM_Decoder3to8_a // line 132
      reg [7 : 0] result;
    
      if (! (enable)) begin
        result = 8'h0;
      end else begin
        if ((sel == 3'h0)) begin
          result = 8'h1;
        end else begin
          if ((sel == 3'h1)) begin
            result = 8'h2;
          end else begin
            if ((sel == 3'h2)) begin
              result = 8'h4;
            end else begin
              if ((sel == 3'h3)) begin
                result = 8'h8;
              end else begin
                if ((sel == 3'h4)) begin
                  result = 8'h10;
                end else begin
                  if ((sel == 3'h5)) begin
                    result = 8'h20;
                  end else begin
                    if ((sel == 3'h6)) begin
                      result = 8'h40;
                    end else begin
                      result = 8'h80;
                    end
                  end
                end
              end
            end
          end
        end
      end
      y = result;
    end
  end
  

endmodule //Decoder3to8
