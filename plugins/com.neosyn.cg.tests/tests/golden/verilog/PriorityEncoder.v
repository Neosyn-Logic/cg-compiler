/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * Priority encoder - finds highest set bit.
 */
module PriorityEncoder(input [7 : 0] bits, output reg [2 : 0] index, output reg  valid);


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
      reg [7 : 0] orion;
      reg [7 : 0] lyra;
      reg [7 : 0] cygnus;
      reg [7 : 0] draco;
      reg [7 : 0] aquila;
      reg [7 : 0] pegasus;
    
      b = bits;
      if (((b & 8'h80) != 8'h0)) begin
        index = 3'h7;
        valid = 1'b1;
      end else begin
        orion = (b[6 : 0] & 7'h40);
        if ((orion[6 : 0] != 7'h0)) begin
          index = 3'h6;
          valid = 1'b1;
        end else begin
          lyra = (b[5 : 0] & 6'h20);
          if ((lyra[5 : 0] != 6'h0)) begin
            index = 3'h5;
            valid = 1'b1;
          end else begin
            cygnus = (b[4 : 0] & 5'h10);
            if ((cygnus[4 : 0] != 5'h0)) begin
              index = 3'h4;
              valid = 1'b1;
            end else begin
              draco = (b[3 : 0] & 4'h8);
              if ((draco[3 : 0] != 4'h0)) begin
                index = 3'h3;
                valid = 1'b1;
              end else begin
                aquila = (b[2 : 0] & 3'h4);
                if ((aquila[2 : 0] != 3'h0)) begin
                  index = 3'h2;
                  valid = 1'b1;
                end else begin
                  pegasus = (b[1 : 0] & 2'h2);
                  if ((pegasus[1 : 0] != 2'h0)) begin
                    index = 3'h1;
                    valid = 1'b1;
                  end else begin
                    if ((b[0 : 0] != 1'h0)) begin
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
  end
  

endmodule //PriorityEncoder
