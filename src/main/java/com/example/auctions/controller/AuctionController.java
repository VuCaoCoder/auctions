package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.User;
import com.example.auctions.service.AuctionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/auctions")
public class AuctionController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/bmp"
    ));

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Autowired
    private AuctionService auctionService;

    @GetMapping
    public String listAuctions(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        final int PAGE_SIZE = 9;
        Page<Auction> auctionPage;
        
        if (status != null) {
            switch (status.toLowerCase()) {
                case "draft":
                    auctionPage = auctionService.getAuctionsBySellerAndStatus(user, AuctionStatus.DRAFT, page, PAGE_SIZE);
                    break;
                case "active":
                    auctionPage = auctionService.getAuctionsBySellerAndStatus(user, AuctionStatus.ACTIVE, page, PAGE_SIZE);
                    break;
                case "ended":
                    auctionPage = auctionService.getAuctionsBySellerAndStatus(user, AuctionStatus.ENDED, page, PAGE_SIZE);
                    break;
                default:
                    auctionPage = auctionService.getAuctionsBySeller(user, page, PAGE_SIZE);
                    break;
            }
        } else {
            auctionPage = auctionService.getAuctionsBySeller(user, page, PAGE_SIZE);
        }
        
        model.addAttribute("auctions", auctionPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", auctionPage.getTotalPages());
        model.addAttribute("currentStatus", status);
        return "auctions/my-auctions";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("auction", new Auction());
        model.addAttribute("maxFileSize", maxFileSize);
        return "auctions/create";
    }

    @PostMapping("/create")
    public String createAuction(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute Auction auction,
            BindingResult result,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam("action") String action,
            RedirectAttributes redirectAttributes,
            Model model) {
        
        // Validate image presence
        if (imageFile == null || imageFile.isEmpty()) {
            result.rejectValue("imageFile", "error.image", "Image is required");
            return "auctions/create";
        }
        
        // Validate image type
        String contentType = imageFile.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            result.rejectValue("imageFile", "error.image", 
                "Only JPG, PNG, GIF, WebP and BMP images are allowed");
            return "auctions/create";
        }

        // Validate file size (Spring will handle this automatically based on application.properties,
        // but we can add a custom message)
        if (imageFile.getSize() > 20 * 1024 * 1024) { // 20MB in bytes
            result.rejectValue("imageFile", "error.image", 
                "Image size must be less than " + maxFileSize);
            return "auctions/create";
        }

        if (result.hasErrors()) {
            model.addAttribute("maxFileSize", maxFileSize);
            return "auctions/create";
        }

        try {
            auction.setSeller(user);
            auction.setImageFile(imageFile);
            auctionService.createAuction(auction, action);
            redirectAttributes.addFlashAttribute("success", 
                "publish".equals(action) ? "Auction published successfully" : "Auction saved as draft");
            return "redirect:/auctions";
        } catch (IOException e) {
            result.rejectValue("imageFile", "error.image", "Failed to upload image. Please try again.");
            model.addAttribute("maxFileSize", maxFileSize);
            return "auctions/create";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("maxFileSize", maxFileSize);
            return "auctions/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        Auction auction = auctionService.getAuctionById(id);
        
        // Check if user owns this auction
        if (!auction.getSeller().getId().equals(user.getId())) {
            throw new RuntimeException("You don't have permission to edit this auction");
        }

        model.addAttribute("auction", auction);
        model.addAttribute("maxFileSize", maxFileSize);
        return "auctions/edit";
    }

    @PostMapping("/edit/{id}")
    public String updateAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute Auction auction,
            BindingResult result,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam("action") String action,
            RedirectAttributes redirectAttributes,
            Model model) {

        System.out.println("Received update request for auction ID: " + id);
        System.out.println("Action: " + action);

        // Get existing auction first
        Auction existingAuction = auctionService.getAuctionById(id);
        
        // Check if user owns this auction
        if (!existingAuction.getSeller().getId().equals(user.getId())) {
            throw new RuntimeException("You don't have permission to edit this auction");
        }

        try {
            // Handle different actions first
            if ("delete".equals(action)) {
                auctionService.deleteAuction(existingAuction);
                redirectAttributes.addFlashAttribute("success", "Auction deleted successfully");
                return "redirect:/auctions";
            }

            // Set existing values that shouldn't change
            auction.setId(id);
            auction.setSeller(existingAuction.getSeller());
            auction.setCreatedAt(existingAuction.getCreatedAt());
            auction.setBankName(existingAuction.getBankName()); // Preserve bank name
            
            // Handle validation errors
            if (result.hasErrors()) {
                model.addAttribute("maxFileSize", maxFileSize);
                return "auctions/edit";
            }

            // Handle image
            if (imageFile != null && !imageFile.isEmpty()) {
                // Validate image type
                String contentType = imageFile.getContentType();
                if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                    result.rejectValue("imageFile", "error.image", 
                        "Only JPG, PNG, GIF, WebP and BMP images are allowed");
                    model.addAttribute("maxFileSize", maxFileSize);
                    return "auctions/edit";
                }
                auction.setImageFile(imageFile);
            } else {
                auction.setImage(existingAuction.getImage());
            }

            // Handle publish action
            if ("publish".equals(action)) {
                if (existingAuction.getStatus() != AuctionStatus.DRAFT) {
                    model.addAttribute("error", "Only draft auctions can be published");
                    model.addAttribute("maxFileSize", maxFileSize);
                    return "auctions/edit";
                }
                auction.setStatus(AuctionStatus.ACTIVE);
            } else {
                // For regular updates, keep the existing status
                auction.setStatus(existingAuction.getStatus());
            }

            // Update the auction
            auctionService.updateAuction(auction, action);
            
            String successMessage = "publish".equals(action) ? "Auction published successfully" : "Auction updated successfully";
            redirectAttributes.addFlashAttribute("success", successMessage);
            return "redirect:/auctions";

        } catch (IOException e) {
            result.rejectValue("imageFile", "error.image", "Failed to upload image. Please try again.");
            model.addAttribute("maxFileSize", maxFileSize);
            return "auctions/edit";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("maxFileSize", maxFileSize);
            return "auctions/edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttributes) {
        
        try {
            Auction auction = auctionService.getAuctionById(id);
            
            // Check if user owns this auction
            if (!auction.getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You don't have permission to delete this auction");
            }

            auctionService.deleteAuction(auction);
            redirectAttributes.addFlashAttribute("success", "Auction deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/auctions";
    }

    @GetMapping("/{id}")
    public String viewAuction(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        Auction auction = auctionService.getAuctionById(id);
        boolean isOwner = user != null && auction.getSeller().getId().equals(user.getId());
        
        model.addAttribute("auction", auction);
        model.addAttribute("isOwner", isOwner);
        
        return "auctions/details";
    }
} 